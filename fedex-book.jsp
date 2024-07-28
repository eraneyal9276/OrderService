<%
request.setCharacterEncoding ("UTF-8");
response.setContentType ("application/json");
response.setCharacterEncoding ("UTF-8");
%><%@
page import = "java.util.UUID" %><%

String orderId = request.getParameter ("orderId");

String sourceName = request.getParameter ("sourceName");
String sourceStreet = request.getParameter ("sourceStreet");
String sourceCity = request.getParameter ("sourceCity");
String sourceCountry = request.getParameter ("sourceCountry");
String sourceZip = request.getParameter ("sourceZip");

String customerFirstName = request.getParameter ("customerFirstName");
String customerLastName = request.getParameter ("customerLastName");
String customerStreet = request.getParameter ("customerStreet");
String customerCity = request.getParameter ("customerCity");
String customerCountry = request.getParameter ("customerCountry");
String customerZip = request.getParameter ("customerZip");
String customerEmail = request.getParameter ("customerEmail");
String customerPhone = request.getParameter ("customerPhone");

if (orderId != null &&
	sourceName != null &&
	sourceStreet != null &&
	sourceCity != null &&
	sourceCountry != null &&
	sourceZip != null &&
	customerFirstName != null &&
	customerLastName != null &
	customerStreet != null &&
	customerCity != null &&
	customerCountry != null &&
	customerZip != null &&
	customerEmail != null &&
	customerPhone != null) {

	response.setStatus (HttpServletResponse.SC_OK);
%>
{"tracking-id":"<%=UUID.randomUUID ()%>"}
<%
} else {
	response.setStatus (HttpServletResponse.SC_BAD_REQUEST);
%>
{"error":"InvalidInput"}
<%
}
%>