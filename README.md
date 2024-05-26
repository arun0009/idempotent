# Idempotent

Idempotent is a lightweight Java library that provides support for idempotency in APIs, making it easier to handle duplicate
requests and ensuring reliable operation in distributed systems. This library integrates seamlessly with Spring applications
and offers idempotency support using Redis and DynamoDB stores.

<img src="./idempotent.png" alt="Idempotent">


## What is Idempotency?
Idempotency is a property in computer science where an operation, when applied multiple times, has the same effect as
applying it once. In the context of APIs, an idempotent operation can be safely retried or replayed without causing
unintended side effects or altering the result beyond the initial application.

## How Idempotency Helps
Idempotency is crucial in distributed systems where network failures, retries, and out-of-order delivery are common.
By ensuring that requests are processed exactly once, idempotency prevents duplicate actions, maintains data integrity,
and improves overall system reliability.

In API development, idempotency helps in the following ways:

* **Prevents Duplicate Requests**: Idempotency ensures that repeated requests with the same parameters have no additional effect,
		reducing the risk of unintended side effects caused by duplicate processing.
* **Simplifies Error Handling**: With idempotent APIs, error handling becomes more straightforward as clients can safely retry
		failed requests without worrying about causing duplicate actions or data corruption.
* **Improves Scalability**: Idempotency allows systems to gracefully handle high loads and spikes in traffic by efficiently
		processing duplicate requests without overloading backend services or causing resource contention.

## Features
* **Integration with Spring**: Idempotent seamlessly integrates with Spring applications, providing annotations and utilities to
		easily add idempotency support to APIs.
* **Support for [Redis](idempotent-redis/README.md) and [DynamoDB](idempotent-dynamo/README.md)**: Idempotent offers storage
		adapters for Redis and DynamoDB, allowing developers to choose the backend that best suits their requirements.
* **Simple Annotation-based Configuration**: Adding idempotency to APIs is as simple as annotating the relevant methods
		with [@Idempotent](idempotent-core/src/main/java/io/github/arun0009/idempotent/core/annotation/Idempotent.java).

## Getting Started

To add idempotency to your Spring APIs using Idempotent, follow these steps:

1. Add the Idempotent dependency to your project.
2. Configure the storage backend ([Redis](idempotent-redis/README.md) or [DynamoDB](idempotent-dynamo/README.md)) in your Spring application context.
3. Annotate the desired API methods with [@Idempotent](idempotent-core/src/main/java/io/github/arun0009/idempotent/core/annotation/Idempotent.java)
4. and specify the key and time-to-live (TTL) for idempotent requests.
```java
@Idempotent(key = "#paymentDetails", ttlInSeconds = 60, hashKey=true)
@PostMapping("/payments")
public PaymentResponse postPayment(@RequestBody PaymentDetails paymentDetails) {
// Method implementation
}
```
## Contributing
Contributions to Idempotent are welcome! Whether you want to report a bug, suggest a feature, or contribute code, please feel free
to open an issue or submit a pull request on GitHub.

By leveraging Idempotent in your Spring applications, you can ensure the reliability and integrity of your APIs, even in the face of
network failures and high concurrency. Start using Idempotent today to simplify error handling, improve scalability, and deliver a
more robust experience to your users.
