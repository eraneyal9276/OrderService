package com.eraneyal.order;

import java.util.Map;

/**
  * Represents third party API specific handlers responsible for constructing HTTP GET
  * requests and parsing the responses for courier booking APIs.
  * <p>
  * NOTE 1: To simplify the implementation, I assumed all the booking API calls will use
  * 	  	HTTP GET requests. This hierarchy can be easily extended to support POST
  * 	  	requests too.
  * <p>
  * NOTE 2: In a real world scenario, any booking API will probably require some additional
  * 		authentication parameters. This may require extending this interface (for
  * 		example by adding a method that generates the HTTP request headers, if an
  * 		authentication token should be passed in the headers).
  */

public interface CourierBookingHandler {

/**
  * Returns the passed order details an HTTP requests parameters.
  * <p>
  * @param orderId the order identifier
  * @param allocation the allocation details of the entire order or a subset of the order
  * @param customer the customer details
  * @return the passed order details an HTTP requests parameters
  * @exception CourierBookingHandlerException in case of failure to construct the HTTP
  * 		   request parameters
  */

	public Map<String,String> getHTTPRequestParams (String orderId, Allocation allocation, Customer customer)
    	throws CourierBookingHandlerException;

/**
  * Returns the complete URI for the HTTP GET request.
  * <p>
  * @param baseAddr the base address of the courier API call
  * @param params the request parameters
  * @return the complete URI for the HTTP GET request
  * @exception CourierBookingHandlerException in case of failure to construct the URI
  */

	public String getBookingURI (String baseAddr, Map<String,String> params)
		throws CourierBookingHandlerException;

/**
  * Returns the tracking identifier returned from the third-party courier API.
  * <p>
  * @param response the HTTP response
  * @return the tracking identifier returned from the third-party courier API
  * @exception CourierBookingHandlerException in case of any failures to parse the response
  */

	public String getTrackingId (String response)
		throws CourierBookingHandlerException;

}
