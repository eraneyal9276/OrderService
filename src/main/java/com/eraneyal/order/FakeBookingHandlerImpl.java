package com.eraneyal.order;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
  * Implements a fake handler that doesn't pass any parameters to the target URL, and
  * generates a fake tracking identifier, ignoring the target URL's response.
  * <p>
  * This makes it possible to test the booking API call with any URL that returns a 200
  * status code, such as http://www.example.com
  */

public class FakeBookingHandlerImpl extends CourierBookingHandlerImpl
{

/**
  * Returns the passed order details an HTTP requests parameters.
  * <p>
  * @param order the order identifier
  * @param allocation the allocation details of the entire order or a subset of the order
  * @param customer the customer details
  * @return the passed order details an HTTP requests parameters
  * @exception CourierBookingHandlerException in case of failure to construct the HTTP
  * 		   request parameters
  */

    @Override
    public Map<String, String> getHTTPRequestParams (
        String order,
        Allocation allocation,
        Customer customer)
        throws CourierBookingHandlerException
    {
        return new HashMap<> ();
    }

/**
  * Returns the tracking identifier returned from the third-party courier API.
  * <p>
  * @param response the HTTP response
  * @return the tracking identifier returned from the third-party courier API
  * @exception CourierBookingHandlerException in case of any failures to parse the response
  */

    @Override
    public String getTrackingID (String response)
        throws CourierBookingHandlerException
    {
        return UUID.randomUUID ().toString ();
    }

}
