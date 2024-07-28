FROM tomcat:latest

WORKDIR /usr/local/tomcat
RUN mkdir -p webapps/courier/
COPY deliverit-book.jsp webapps/courier/.
COPY fedex-book.jsp webapps/courier/.
