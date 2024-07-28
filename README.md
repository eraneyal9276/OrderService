# Note

The structure of this project is based on the ["Implementing Microservices with Akka"](https://doc.akka.io/guide/microservices-tutorial/index.html)
Akka tutorial, and more specifically on the [Event Sourced Cart entity](https://doc.akka.io/guide/microservices-tutorial/entity.html)
and the corresponding [gRPC Cart service](https://doc.akka.io/guide/microservices-tutorial/grpc-service.html) of that tutorial.

# Running the order service

* Install docker, docker compose and maven.

* Start some docker containers in order to run a PostgreSQL DB (required for persistence
  of the event store), as well as an Apache Tomcat instance that simulates two courier delivery booking APIs
  (executed when you run the PackItems endpoint)

```
docker-compose up -d
```

* Create the PostgreSQL tables:

```
docker exec -i postgres-event-db psql -U order-service -t < ddl-scripts/create_tables.sql
```

or

```
more ddl-scripts\create_tables.cql | docker exec -i postgres-event-db psql -U order-service
```

* Start a first node:

```
mvn compile exec:exec -DAPP_CONFIG=local1.conf
```

* (Optional) Start another node with different ports:

```
mvn compile exec:exec -DAPP_CONFIG=local2.conf
```

* Check for service readiness

```
curl http://localhost:9101/ready
```

# Executing the Endpoints

##Notes

1. The **grpcurl** tool is required for making gRPC calls from the command line (you can't use curl).
1. If you don't have **grpcurl** on your machine, you can use **docker run fullstorydev/grpcurl**
instead of **grpcurl**. In that case, you'll also need to change **127.0.0.1** to **host.docker.internal** in the following examples in order to communicate with the local service.
2. If you are running the command in a Windows shell, you may have to escape the double quotes. For example - use **"""itemId"""** instead of **"itemId"**.

##API examples

###1. Receive Order:

This creates a new Order for the given order identifier, if one doesn't already exist. It will fail if the Order already exists for the given identifier. It persists an **OrderReceived**
event, and triggers asynchronous creation of allocations for the order. When the allocations are created, an **OrderAllocationsReceived** event is persisted.

	grpcurl -d '{"order_id":"order1","items":[{"item_id":"234323","name":"coke","quantity":5},{"item_id":"353464","name":"sugar","quantity":3},{"item_id":"46758543","name":"bread","quantity":2}], "customer":{"first_name":"Eran","last_name":"Eyal","address":{"street":"Some Street 42","city":"Some City","zip_code":12345,"country":"Israel"},"email":"somebody@gmail.com","mobile_phone":"0521234567"}}' -plaintext 127.0.0.1:8101 OrderService.OrderService.ReceiveOrder

###Successful Response:

```
{
    "ok": true
}
```

###2. Fetch Order Details:

This returns the complete order details along with its allocations.

	grpcurl -d '{"order_id":"order1"}' -plaintext 127.0.0.1:8101 OrderService.OrderService.FetchOrderDetails

###Successful Response:

```
{
  "order_id": "order1",
  "allocations": [
    {
      "allocation_id": "1",
      "name": "Outlet Store",
      "address": {
        "street": "Bialik 89",
        "city": "Ramat Gan",
        "country": "Israel",
        "zip_code": 64722
      },
      "items": [
        {
          "item_id": "46758543",
          "name": "bread",
          "quantity": 2
        }
      ],
      "courier": "DeliverIt",
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-28T08:56:30.941376900Z"
        }
      ]
    },
    {
      "allocation_id": "2",
      "name": "TLV Warehouse",
      "address": {
        "street": "Namir 15",
        "city": "Tel Aviv",
        "country": "Israel",
        "zip_code": 12345
      },
      "items": [
        {
          "item_id": "234323",
          "name": "coke",
          "quantity": 5
        },
        {
          "item_id": "353464",
          "name": "sugar",
          "quantity": 3
        }
      ],
      "courier": "FedEx",
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-28T08:56:30.941376900Z"
        }
      ]
    }
  ],
  "customer": {
    "first_name": "Eran",
    "last_name": "Eyal",
    "address": {
      "street": "Some Street 42",
      "city": "Some City",
      "country": "Israel",
      "zip_code": 12345
    },
    "email": "somebody@gmail.com",
    "mobile_phone": "0521234567"
  }
}
```

###3. Pack Items:

If the given order allocation is already packed, the previously persisted tracking identifier is returned.
Otherwise, a courier API is called to book delivery for the requested order allocation, and the items of this
order allocation are marked as packed after successful response from the courier.
An **OrderAllocationPacked** event, which records both the new status of the order allocation, and the tracking
identifier returned by the courier API, is persisted in this case. The new tracking identifier is returned.

	grpcurl -d '{"order_id":"order1","allocation_id":"1"}' -plaintext 127.0.0.1:8101 OrderService.OrderService.PackItems

###Successful Response:

```
{
  "tracking_id": "F711B1E2DD424E3ABD80DDFC88AC9264"
}
```

###After this call, running FetchOrderDetails again will give a result like this:

```
{
  "order_id": "order1",
  "allocations": [
    {
      "allocation_id": "1",
      "name": "Outlet Store",
      "address": {
        "street": "Bialik 89",
        "city": "Ramat Gan",
        "country": "Israel",
        "zip_code": 64722
      },
      "items": [
        {
          "item_id": "46758543",
          "name": "bread",
          "quantity": 2
        }
      ],
      "courier": "DeliverIt",
      "tracking_id": "F711B1E2DD424E3ABD80DDFC88AC9264", <--- the tracking identifier
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-28T08:56:30.941376900Z"
        },
        {
          "type": "PACKED", <--- the new order allocation status
          "timestamp": "2024-07-28T09:10:08.288077700Z"
        }
      ]
    },
    {
      "allocation_id": "2",
      "name": "TLV Warehouse",
      "address": {
        "street": "Namir 15",
        "city": "Tel Aviv",
        "country": "Israel",
        "zip_code": 12345
      },
      "items": [
        {
          "item_id": "234323",
          "name": "coke",
          "quantity": 5
        },
        {
          "item_id": "353464",
          "name": "sugar",
          "quantity": 3
        }
      ],
      "courier": "FedEx",
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-28T08:56:30.941376900Z"
        }
      ]
    }
  ],
  "customer": {
    "first_name": "Eran",
    "last_name": "Eyal",
    "address": {
      "street": "Some Street 42",
      "city": "Some City",
      "country": "Israel",
      "zip_code": 12345
    },
    "email": "somebody@gmail.com",
    "mobile_phone": "0521234567"
  }
}
```

###4. Tracking Update:

This updates the delivery status of an order allocation. It should be executed by the courier that delivers the items of an order allocation.
It persists a **TrackingUpdated** event, which records the new status of the order allocation.

	grpcurl -d '{"order_id":"order1","allocation_id":"1","status":"PICKED_BY_COURIER"}' -plaintext 127.0.0.1:8101 OrderService.OrderService.TrackingUpdate

###Successful Response:

```
{
    "ok": true
}
```

* The valid statuses are "PICKED_BY_COURIER" (or 0), "ENROUTE_TO_CUSTOMER" (or 1) and "DELIVERED" (or 2).
* The statuses must be supplied in order. i.e. you can't request "ENROUTE_TO_CUSTOMER" after you already requested "DELIVERED".
* On the other hand, it is allowed to skip some of the statuses. For example, you can request "DELIVERED" without first requesting
"ENROUTE_TO_CUSTOMER" (assuming the requested allocation is already marked as packed).

#After this call, running FetchOrderDetails again will give a result like this:

```
{
  "order_id": "order1",
  "allocations": [
    {
      "allocation_id": "1",
      "name": "Outlet Store",
      "address": {
        "street": "Bialik 89",
        "city": "Ramat Gan",
        "country": "Israel",
        "zip_code": 64722
      },
      "items": [
        {
          "item_id": "46758543",
          "name": "bread",
          "quantity": 2
        }
      ],
      "courier": "DeliverIt",
      "tracking_id": "F711B1E2DD424E3ABD80DDFC88AC9264",
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-28T08:56:30.941376900Z"
        },
        {
          "type": "PACKED",
          "timestamp": "2024-07-28T09:10:08.288077700Z"
        },
        {
          "type": "PICKED_BY_COURIER", <--- the new order allocation status
          "timestamp": "2024-07-28T09:12:45.428948400Z"
        }
      ]
    },
    {
      "allocation_id": "2",
      "name": "TLV Warehouse",
      "address": {
        "street": "Namir 15",
        "city": "Tel Aviv",
        "country": "Israel",
        "zip_code": 12345
      },
      "items": [
        {
          "item_id": "234323",
          "name": "coke",
          "quantity": 5
        },
        {
          "item_id": "353464",
          "name": "sugar",
          "quantity": 3
        }
      ],
      "courier": "FedEx",
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-28T08:56:30.941376900Z"
        }
      ]
    }
  ],
  "customer": {
    "first_name": "Eran",
    "last_name": "Eyal",
    "address": {
      "street": "Some Street 42",
      "city": "Some City",
      "country": "Israel",
      "zip_code": 12345
    },
    "email": "somebody@gmail.com",
    "mobile_phone": "0521234567"
  }
}
```
