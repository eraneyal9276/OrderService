package com.eraneyal.order;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.HashMap;
import java.util.Map;

/**
  * Implements FedEx specific handler responsible for constructing HTTP GET requests and
  * parsing the responses for FedEx booking API.
  */

public class FedExBookingHandlerImpl extends CourierBookingHandlerImpl {

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

	@Override
	public Map<String, String> getHTTPRequestParams (String orderId, Allocation allocation, Customer customer)
		throws CourierBookingHandlerException
	{
		if (orderId == null) {
			throw new CourierBookingHandlerException ("Missing order identifier");
		}
		if (allocation == null) {
			throw new CourierBookingHandlerException ("Missing order allocation");
		}
		if (customer == null) {
			throw new CourierBookingHandlerException ("Missing order customer");
		}

		var params = new HashMap<String, String> ();

		params.put("orderId", orderId + '-' + allocation.getID ());

// -- source location details
		params.put ("sourceName", allocation.getName ());
		params.put ("sourceStreet", allocation.getAddress ().getStreet ());
		params.put ("sourceCity", allocation.getAddress ().getCity ());
		params.put ("sourceCountry", allocation.getAddress ().getCountry ());
		params.put ("sourceZip", Integer.toString (allocation.getAddress ().getZipCode ()));

// -- customer (destination) details
		params.put ("customerFirstName", customer.getFirstName ());
		params.put ("customerLastName", customer.getLastName ());
		params.put ("customerStreet", customer.getAddress ().getStreet ());
		params.put ("customerCity", customer.getAddress ().getCity ());
		params.put ("customerCountry", customer.getAddress ().getCountry ());
		params.put ("customerZip", Integer.toString (customer.getAddress ().getZipCode ()));
		params.put ("customerEmail", customer.getEMail ());
		params.put ("customerPhone", customer.getMobilePhone ());

		return params;
	}

/**
  * Returns the tracking identifier returned from the third-party courier API.
  * <p>
  * @param response the HTTP response
  * @return the tracking identifier returned from the third-party courier API
  * @exception CourierBookingHandlerException in case of any failures to parse the response
  */

	@Override
	public String getTrackingId (String response)
		throws CourierBookingHandlerException
	{
		if (response == null) {
			throw new CourierBookingHandlerException ("Missing response");
		}

		String ident = null;
		JSONParser parser = new JSONParser ();
		try {
			JSONObject json = (JSONObject) parser.parse (response);
			if (json.containsKey ("tracking-id")) {
				ident = json.get ("tracking-id").toString ();
			}
		}
		catch (ParseException parseEx) {
			throw new CourierBookingHandlerException ("Failed to parse response JSON", parseEx);
		}
		if (ident == null || ident.isEmpty ()) {
			throw new CourierBookingHandlerException ("Missing tracking identifier");
		}

		return ident;
	}

}
