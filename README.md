# Running the order service

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

# Executing Running the APIs

##Notes

1. If you don't have grpcurl on your machine, and you have docker, you can use "docker run fullstorydev/grpcurl"
instead of "grpcurl". In that case you also need to change 127.0.0.1 to host.docker.internal in order to communicate with the local service.
2. If you are running the command in a Windows shell, you might have to escape the quotes. For example - """itemId""" instead of "itemId".

##API examples

###1. Receive Order:

	grpcurl -d '{"order_Id":"order1","items":[{"item_id":"234323","name":"coke","quantity":5},{"item_id":"353464","name":"sugar","quantity":3},{"item_id":"46758543","name":"bread","quantity":2}],"customer":{"first_name":"Eran","last_name":"Eyal","address":{"street":"Some Street 42","city":"Some City","zip_code":12345,"country":"Israel"},"email":"somebody@gmail.com","mobile_phone":"0521234567"}}' -plaintext 127.0.0.1:8101 OrderService.OrderService.ReceiveOrder

###Successful Response:

```
{
    "ok": true
}
```

###2. Fetch Order Details:

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
          "timestamp": "2024-07-25T11:16:41.718690900Z"
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
          "timestamp": "2024-07-25T11:16:41.718690900Z"
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

	grpcurl -d '{"order_id":"order1","allocation_id":"1"}' -plaintext 127.0.0.1:8101 OrderService.OrderService.PackItems

###Successful Response:

```
{
    "ok": true
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
      "tracking_id": "211E60C2CE0245CAB93C5B0D81704B0C",
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-25T11:16:41.718690900Z"
        },
        {
          "type": "PACKED",
          "timestamp": "2024-07-25T11:33:56.036335700Z"
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
          "timestamp": "2024-07-25T11:16:41.718690900Z"
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
```

###4. Tracking Update:

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
      "tracking_id": "211E60C2CE0245CAB93C5B0D81704B0C",
      "statuses": [
        {
          "type": "ALLOCATED",
          "timestamp": "2024-07-25T11:16:41.718690900Z"
        },
        {
          "type": "PACKED",
          "timestamp": "2024-07-25T11:33:56.036335700Z"
        },
        {
          "type": "PICKED_BY_COURIER",
          "timestamp": "2024-07-25T11:39:41.925289500Z"
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
          "timestamp": "2024-07-25T11:16:41.718690900Z"
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
```
