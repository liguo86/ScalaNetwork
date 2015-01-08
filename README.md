ScalaNetwork 0.1.0
==================

A **Neural Network implementation** with Scala & [Breeze](https://github.com/scalanlp/breeze)

# Features

## Network
ScalaNetwork supports following layered neural network implementation:

* *Fully-connected* Neural Network : $f(Wx + b)$
* *Fully-connected* Rank-3 Tensor Network : $f(v_1^T Q^{[1:k]} v_2 + Lv + b)$
* *Fully-connected* Auto Encoder
* *Fully-connected* Stacked Auto Encoder

## Training Methodology
ScalaNetwork supports following training methodologies:

* Stochastic Gradient Descent w/ L1-, L2-regularization, Momentum.
* [AdaGrad](http://www.magicbroom.info/Papers/DuchiHaSi10.pdf)
* [AdaDelta](http://www.matthewzeiler.com/pubs/googleTR2012/googleTR2012.pdf)

## Activation Function
ScalaNetwork supports following activation functions:

* Sigmoid
* HyperbolicTangent
* Rectifier
* Softplus

# Usage

`Network.apply(Activation, Int*)` generates fully-connected network:

```scala
// 2(input) -> 4(hidden) -> 1(output)
val network = Network(Sigmoid, 2, 4, 1)
//Training with Squared Error
val trainer = new BasicTrainer(network, SquaredErr)
//Training gives validation error 
val err = trainer.trainWithValidation(set, validation)
```

Also you can use `new BasicNetwork(Seq[Layer], Probability)` to generate a basic network,
and `new AutoEncoder(Reconstructable, Probability)` to generate a single-layered autoencoder.

For further usage, please read scaladocs.

# Blueprint

ScalaNetwork will support these implementations:

* Recursive Auto Encoder (RAE)
* Unfolded Recursive Auto Encoder (URAE)
* Recursive Neural Tensor Network (RNTN)

Also ScalaNetwork will support these features:

* Input-dependent Weight

## Current Status

Next version(v0.2) will support RAE, URAE
