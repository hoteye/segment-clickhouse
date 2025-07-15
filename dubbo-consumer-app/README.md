# Dubbo Consumer Application

This project is a simple example of a Dubbo consumer application that demonstrates how to make remote service calls using Apache Dubbo.

## Project Structure

```
dubbo-consumer-app
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── example
│   │   │           ├── consumer
│   │   │           │   ├── ConsumerApplication.java
│   │   │           │   └── service
│   │   │           │       └── ExampleServiceConsumer.java
│   │   └── resources
│   │       ├── application.properties
│   │       └── dubbo
│   │           └── consumer.xml
├── pom.xml
└── README.md
```

## Prerequisites

- Java 8 or higher
- Maven

## Setup

1. Clone the repository:
   ```
   git clone <repository-url>
   cd dubbo-consumer-app
   ```

2. Update the `application.properties` file with your service registry address and other configurations.

3. Build the project using Maven:
   ```
   mvn clean install
   ```

## Running the Application

To run the Dubbo consumer application, execute the following command:

```
mvn spring-boot:run
```

## Usage

Once the application is running, it will automatically call the remote service defined in `ExampleServiceConsumer.java`. You can modify the service call logic as needed.

## License

This project is licensed under the MIT License.