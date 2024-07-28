<%
request.setCharacterEncoding ("UTF-8");
response.setContentType ("application/json");
response.setCharacterEncoding ("UTF-8");
%><%@
page import = "java.util.UUID" %><%

String orderId = request.getParameter ("orderId");

String fromName = request.getParameter ("fromName");
String fromStreet = request.getParameter ("fromStreet");
String fromCity = request.getParameter ("fromCity");
String fromCountry = request.getParameter ("fromCountry");
String fromZip = request.getParameter ("fromZip");

String toFirstName = request.getParameter ("toFirstName");
String toLastName = request.getParameter ("toLastName");
String toStreet = request.getParameter ("toStreet");
String toCity = request.getParameter ("toCity");
String toCountry = request.getParameter ("toCountry");
String toZip = request.getParameter ("toZip");
String toEmail = request.getParameter ("toEmail");
String toPhone = request.getParameter ("toPhone");

if (orderId != null &&
	fromName != null &&
	fromStreet != null &&
	fromCity != null &&
	fromCountry != null &&
	fromZip != null &&
	toFirstName != null &&
	toLastName != null &
	toStreet != null &&
	toCity != null &&
	toCountry != null &&
	toZip != null &&
	toEmail != null &&
	toPhone != null) {

	response.setStatus (HttpServletResponse.SC_OK);
%>
{"tracking-number":"<%=UUID.randomUUID ().toString ().replace ("-","").toUpperCase ()%>"}
<%
} else {
	response.setStatus (HttpServletResponse.SC_BAD_REQUEST);
%>
{"error":"InvalidInput"}
<%
}
%>