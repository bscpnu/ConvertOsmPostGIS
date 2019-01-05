# Part 1. ConvertOsmPostGIS
#### Program Assigment SDB Class, Pusan National University 2017
#### By Imam Mustafa Kamal (201683609)

compile- and run-time dependencies :
- PostgreSQL
- PostGIS

other requirements (we provided it root directory):
- data.osm (download it from openstreetmap.org), in this case we use Manchester map
- Java library : postgresql-42.1.1.jre6.jar and nodes2postgis.jar

- How to Run?
1. Open this program by using java IDE. 
2. Right click it, and change run configuration for adding -d hostname:port/databasename -u username -W password "Path\....\data.osm" -f      feature.json (for extracting information in data.osm).
   and the last add our custom logging.properties to capture the output.
   the figure of configuration as follow: 
   ![configuration](https://user-images.githubusercontent.com/29518994/27262042-d95f6dec-5489-11e7-920c-1d9213a8d714.png)
3. Statistical output, from console
   ![ouput statistic](https://user-images.githubusercontent.com/29518994/27262134-58744f02-548b-11e7-8751-885ff482d18c.png)
4. Outputs
   Open your PostGIS database it will generates a lot of table as follows
   ![tabel](https://user-images.githubusercontent.com/29518994/27262069-2ffb3f0a-548a-11e7-8071-24d7ee371fa2.png)

to be continued in https://github.com/bscpnu/OpenLayersFromPostGIS for displaying data from PostGIS to website
