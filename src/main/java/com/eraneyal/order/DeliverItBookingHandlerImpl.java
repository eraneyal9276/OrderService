package com.eraneyal.order;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements DeliverIt specific handler responsible for constructing HTTP GET requests and
 * parsing the responses for DeliverIt booking API.
 */

public class DeliverItBookingHandlerImpl extends CourierBookingHandlerImpl
{

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

// -- from
		params.put ("fromName", allocation.getName ());
		params.put ("fromStreet", allocation.getAddress ().getStreet ());
		params.put ("fromCity", allocation.getAddress ().getCity ());
		params.put ("fromCountry", allocation.getAddress ().getCountry ());
		params.put ("fromZip", Integer.toString (allocation.getAddress ().getZipCode ()));

// -- to
		params.put ("toFirstName", customer.getFirstName ());
		params.put ("toLastName", customer.getLastName ());
		params.put ("toStreet", customer.getAddress ().getStreet ());
		params.put ("toCity", customer.getAddress ().getCity ());
		params.put ("toCountry", customer.getAddress ().getCountry ());
		params.put ("toZip", Integer.toString (customer.getAddress ().getZipCode ()));
		params.put ("toEmail", customer.getEMail ());
		params.put ("toPhone", customer.getMobilePhone ());

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
			if (json.containsKey ("tracking-number")) {
				ident = json.get ("tracking-number").toString ();
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
