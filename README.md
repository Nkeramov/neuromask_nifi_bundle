# Neuromask NiFi bundle

This project provides Apache NiFi custom processors package for working with Neuromask.

Neuromask is a project to create a smart device in the form of a mask with sensors 
of various types that collects biomedical data from a person. The collected data is 
recorded and transmitted first to a mobile application in the form of a binary data 
package, and then sent to the company's servers for uploading to the analytical 
platform. The device was built on ESP8266.

The platform analyzes the performance of the cardiovascular, external and autonomic 
nervous systems, as well as data on the state of the environment and types of 
physical activity at a particular point in time.

The ETL pipeline was built on Apache NiFi.

At the moment, the package contains one processor that allows you to convert 
a binary data package into a set of JSON records for subsequent uploading 
to the platform storage.



