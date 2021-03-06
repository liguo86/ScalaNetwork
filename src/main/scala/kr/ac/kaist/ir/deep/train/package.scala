package kr.ac.kaist.ir.deep

import breeze.stats.distributions.Gaussian
import kr.ac.kaist.ir.deep.fn._
import org.apache.spark.AccumulatorParam
import org.apache.spark.storage.StorageLevel

import scala.annotation.tailrec
import scala.concurrent.duration._

/**
 * Package for training.
 */
package object train {

  /** Type of Corruption */
  trait Corruption extends (ScalarMatrix ⇒ ScalarMatrix) with Serializable

  /**
   * __Input Corruption__: Drop input as zero.
   *
   * If network uses drop-out training, we recommend that you do not use this.
   *
   * @note If the presence probability is `P%`, then this corruption leaves `P%` entries of the matrix
   *
   * @param presence probability of __not-dropped__. `(default 95% = 0.95)`
   *
   * @example
   * {{{var corrupt = DroppingCorruption(presence = 0.99)
   *        var corrupted = corrupt(vector)}}}
   */
  case class DroppingCorruption(presence: Float = 0.95f) extends Corruption {
    /**
     * Do corruption
     *
     * @param v1 Matrix to be corrupted
     * @return corrupted vector
     */
    override def apply(v1: ScalarMatrix): ScalarMatrix =
      v1 mapValues { x ⇒ if (Math.random() > presence) 0.0f else x}
  }
  
  /**
   * __Input Corruption__: Gaussian
   *
   * @param mean __Mean__ of noise `(default 0.0)`
   * @param variance __Variance__ of noise `(default 0.1)`
   *
   * @example
   * {{{var corrupt = GaussianCorruption(variance = 0.1)
   *       var corrupted = corrupt(vector)}}}
   */
  case class GaussianCorruption(mean: Double = 0.0, variance: Double = 0.1) extends Corruption {
    /**
     * Gaussian Distribution
     */
    private lazy val distro = Gaussian distribution(mean, variance)

    /**
     * Do corruption
     *
     * @param v1 Matrix to be corrupted
     * @return corrupted vector
     */
    override def apply(v1: ScalarMatrix): ScalarMatrix =
      v1 mapValues { x ⇒ x + distro.draw().toFloat}
  }

  /**
   * __Criteria__: When to stop training
   *
   * This case class defines when to stop training. Training stops if one of the following condition is satisfied.
   *
  - #Iteration ≥ maxIter
   - #Iteration ≥ current patience value, which is calculated by `max(patience, bestIteration * patienceStep)`
   - Amount of loss < lossThreshold
   *
   * Validation is done for each `validationFreq` iterations,
   * and whenever current/best loss ratio below improveThreshold,
   * that iteration is marked as best iteration.
   *
   * @param maxIter __maximum mini-batch__ iteration count `(default 100,000)`
   * @param waitAfterUpdate __multiplier__ for calculating patience `(default 1 := Wait lastupdate# * 1 after update)`
   * @param improveThreshold __threshold__ that iteration is marked as "improved" `(default 99.5% = 0.995)`
   * @param lossThreshold __maximum-tolerant__ loss value. `(default 0.0001)`
   * @param validationFreq __multiplier__ used for count for validation. `(default 1.0f)`
   *                       Validation checked whenever (validationFreq) * (#epoch for 1 training batch).
   *                       where #epoch for 1 iteration = round(1 / miniBatchFraction).
   */
  case class StoppingCriteria(maxIter: Int = 100000,
                              waitAfterUpdate: Int = 1,
                              improveThreshold: Float = 0.995f,
                              lossThreshold: Float = 0.0001f,
                              validationFreq: Float = 1.0f)
    extends Serializable

  /**
   * __Criteria__: How to train (for [[SingleThreadTrainStyle]])
   *
   * This case class defines how to train the network. Training parameter is defined in this class.
   *
   * @param miniBatchFraction size of __mini-batch__ `(default 0.01 = 1%)`
   *                          If below or equal to zero, then this indicates no batch training (i.e. just go through once.)
   */
  case class SimpleTrainingCriteria(override val miniBatchFraction: Float = 0.01f) extends TrainingCriteria

  /**
   * __Criteria__: How to train (for [[DistBeliefTrainStyle]])
   *
   * This case class defines how to train the network. Training parameter is defined in this class.
   *
   * @param miniBatchFraction size of __mini-batch__ `(default 0.01 = 1%)`
   *                          If below or equal to zero, then this indicates no batch training (i.e. just go through once.)
   * @param submitInterval Time interval between batch submission. `(default 1.minute)`
   * @param updateStep number of __mini-batches__ between update `(default 2)`
   * @param fetchStep number of __mini-batches__ between fetching `(default 10)`
   * @param numCores number of __v-cores__ in the spark cluster. `(default 1)`
   * @param repartitionOnStart true if do repartition when define training/testing RDD instances. `(default false)`
   * @param storageLevel StorageLevel that will be used in Spark. `(default DISK_ONLY_2)`
   *
   * @note We recommend set numCores as similar as possible with allocated spark v-cores.
   */
  case class DistBeliefCriteria(override val miniBatchFraction: Float = 0.01f,
                                submitInterval: Duration = 30.seconds,
                                updateStep: Int = 2,
                                fetchStep: Int = 10,
                                numCores: Int = 1,
                                repartitionOnStart: Boolean = true,
                                storageLevel: StorageLevel = StorageLevel.MEMORY_ONLY) extends TrainingCriteria

  /**
   * Accumulator Param object for DistBelief Train Style.
   */
  implicit object WeightAccumulator extends AccumulatorParam[IndexedSeq[ScalarMatrix]] {
    /**
     * Add in place function
     * @param r1 left hand side
     * @param r2 right hand side
     * @return r1 + r2 in r1
     */
    override def addInPlace(r1: IndexedSeq[ScalarMatrix], r2: IndexedSeq[ScalarMatrix]): IndexedSeq[ScalarMatrix] = {
      r1 :+= r2
    }

    /**
     * Zero value
     * @param initialValue initial value
     * @return initial zero value.
     */
    override def zero(initialValue: IndexedSeq[ScalarMatrix]): IndexedSeq[ScalarMatrix] =
      initialValue.map {
        matx ⇒
          ScalarMatrix $0(matx.rows, matx.cols)
      }
  }

  /**
   * Non-blocking await 
   */
  object AsyncAwait extends Serializable {

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent._
    
    /**
     * Tail-recursive version of non-block pending
     * @param f Future object to wait
     * @param interval Duration object specifying waiting time.
     */
    @tailrec
    final def ready(f: Future[_], interval: Duration): Unit = try {
      Await.ready(f, interval)
    } catch {
      case _: TimeoutException ⇒ ready(f, interval)
    }

    /**
     * Tail-recursive version of non-block pending
     * @param interval Duration object specifying waiting time.
     * @param f Future objects to wait
     */
    final def readyAll(interval: Duration, f: Future[Any]*): Unit =
      ready(Future.sequence(f.seq), interval)
  }

  /**
   * __Input Corruption__: Never corrupts input
   *
   * @example 
   * {{{var corrupt = NoCorruption(variance = 0.1)
   *       var corrupted = corrupt(vector)}}}
   */
  case object NoCorruption extends Corruption {

    /**
     * Identity.
     * @param v1 to be corrupted
     * @return the vector
     */
    override def apply(v1: ScalarMatrix) = v1
  }

}
