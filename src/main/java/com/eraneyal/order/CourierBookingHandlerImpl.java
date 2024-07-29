package com.eraneyal.order;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
  * Implements third party API specific handlers responsible for constructing HTTP GET
  * requests and parsing the responses for courier booking APIs.
  */

public abstract class CourierBookingHandlerImpl implements CourierBookingHandler
{

/**
  * Formats the passed request parameters as a GET request.
  * <p>
  * @param params the request parameters
  * @return a properly formatted GET request
  * @exception CourierBookingHandlerException in case of missing or incomplete parameters
  */

    private String formatRequest (Map<String,String> params)
        throws CourierBookingHandlerException
    {
        if (params == null) {
            throw new CourierBookingHandlerException ("Missing request parameters");
        }

        StringBuilder request = new StringBuilder (512);

        try {
            for (var entry : params.entrySet ()) {
                String key = entry.getKey ();
                String value = entry.getValue ();
                if (key == null || key.isEmpty ()) {
                    throw new CourierBookingHandlerException ("Null or empty request parameter");
                }
                if (request.length () > 0) {
                    request.append ('&');
                }
                request.append (key);
                request.append ('=');
                if (value != null) {
                    request.append (URLEncoder.encode (value, "UTF-8"));
                }
            }
        }
        catch (UnsupportedEncodingException encEx) {
            throw new CourierBookingHandlerException ("Unsupported encoding", encEx);
        }

        return request.toString ();
    }

/**
  * Returns the complete URI for the HTTP GET request.
  * <p>
  * @param baseAddr the base address of the courier API call
  * @param params the request parameters
  * @return the complete URI for the HTTP GET request
  * @exception CourierBookingHandlerException in case of failure to construct the URI
  */

    @Override
    public String getBookingURI (String baseAddr, Map<String,String> params)
        throws CourierBookingHandlerException
    {
        if (baseAddr == null) {
            throw new CourierBookingHandlerException ("Missing base URI");
        }

        String request = formatRequest (params);
        StringBuilder requestAddr = new StringBuilder (baseAddr.length () + request.length() + 1);
        requestAddr.append (baseAddr);
        if (baseAddr.indexOf ('?') < 0) {
            requestAddr.append ('?');
        } else {
            requestAddr.append ('&');
        }
        requestAddr.append (request);

        return requestAddr.toString ();
    }

}
