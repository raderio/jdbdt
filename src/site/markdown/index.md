# JDBDT 

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](http://jdbdt.org/MIT_License.html)
[![Maven Central](https://img.shields.io/maven-central/v/org.jdbdt/jdbdt.svg)](https://search.maven.org/#search%7Cga%7C1%7Corg.jdbdt)
[![GitHub release](https://img.shields.io/github/release/JDBDT/jdbdt.svg)](https://github.com/JDBDT/jdbdt/releases)
[![Build status](https://api.travis-ci.org/JDBDT/jdbdt.png?branch=master)](https://travis-ci.org/JDBDT/jdbdt)
[![AppVeyor build status](https://ci.appveyor.com/api/projects/status/647d281hp1b8py3p?svg=false)](https://ci.appveyor.com/project/edrdo/jdbdt)
[![Coverity scan](https://scan.coverity.com/projects/13763/badge.svg?flat=1)](https://scan.coverity.com/projects/edrdo-jdbdt)

JDBDT (Java DataBase Delta Testing) is an open-source Java library for 
testing database applications. The library is designed for automation 
of database setup and validation in test code. 
JDBDT is compact and has no third-party library dependencies (it just the Java 8 SE API internally), 
making it also easy and lightweight to integrate. 

Compared to existing database testing frameworks, the main conceptual novelty
is the possibility of using [&delta;-assertions](DBAssertions.html#DeltaAssertions). 
For details, you may browse the reference documentation available in this site, along with the [Javadoc](apidocs/index.html?org/jdbdt/JDBDT.html) for the JDBDT API and the [JDBDT tutorial](Tutorial.html).

In a nutshell, the main features are as follows:

* The core functionality is exposed by a simple [API facade](Facade.html).


    import static org.jdbdt.JDBDT.*;


* [Tables and queries](DataSources.html) in association to a [database handle](DB.html) 
can be used as data sources.   


    DB db = database("jdbc:myFaveDBEngine://myDB");
    
    Table userTable = 
      table("USER")
	 .columns("ID", "LOGIN", "NAME", "PASSWORD", "CREATED")
	 .build(db);
	 
	Query idQuery = 
	  select("LOGIN", "NAME")
     .from("USER")
     .where("ID = ?")
     .arguments(userId)
     .build(db);


* [Data sets](DataSets.html) are defined programmatically,
without need to maintain external "data files", for instance using data set builders


    DataSet data = 
       builder(t)
      .sequence("ID", 1) // 1, 2, 3, ...
      .sequence("LOGIN", "harry", "mark", "john")
      .sequence("NAME", "Harry H", "Mark M", "John J")
      .sequence("PASSWORD", i -> "password " + i , 1)
      .random("CREATED", Date.valueOf("2015-01-01"), Date.valueOf("2015-12-31"))
      .generate(3) // generate 3 rows 
      .sequence("LOGIN", i -> "guest_" + i, 4)  // "user_4", "user_5", ...
      .sequence("NAME", i -> "Guest User " + i, 4) // "Guest User 4", ...
      .value("password", "samePasswordForAllGuests") 
      .generate(6) // 6 more rows keeping ID sequence and CREATED random filler
      .data();   
   
      
* [Setup methods](DBSetup.html) can be used to define database contents, 
for instance to populate tables, clear them, setting & restoring save points, ..., e.g.


    static Table theTable ;
    static DataSet theinitialStata; 
    
    @BeforeClass
    public static void globalSetup() {
      theTable = ... ;
      theInitialData = ... ;
    }
    
    @Before
    public void perTestSetup() {
       populateIfChanged(initialData);
    }


* [&delta;-assertions](DBAssertions.html#DeltaAssertions) can be used to verify 
database changes incrementally, in addition to standard
assertions for [database state](DBAssertions.html#StateAssertions) 
or [data set comparison](DBAssertions.html#DataSetAssertions), e.g., 

   
    @Test
    public void testUserInsertion() {
      User uJohn = ...;
      DataSet newRow = 
         data(theTable)
        .row(999, "john", "John", "jpass", Date.valueOf("2016-01-01"));
	  sut.insertUser( uJohn ); 
	  // Verify the insertion; assertion fails if other changes are detected
	  assertInserted(newRow); 
	}
	
	@Test
	public void testHarmlessQuery() {
	  User u = sut.getUser("john");
	  ... // standard assertions
	  assertUnchanged(theTable); // no delta, query is really harmless!
    }
    
* Assertions and setup operations can be [logged onto (optionally compressed) XML files](Logs.html).


	<jdbdt-log-message time="..." version="...">
    ...
	  <delta-assertion>
        <expected>
          <old-data count="0"/>
          <new-data count="0"/>
        </expected>
        <errors>
      	  <old-data>
            <expected count="0"/>
            <actual count="1">
              <row>
                <column java-type="java.lang.String" label="LOGIN">linus</column>
                <column java-type="java.lang.String" label="NAME">Linus Torvalds</column>
                <column java-type="java.lang.String" label="PASSWORD">linux</column>
                <column java-type="java.sql.Date" label="CREATED">2015-01-01</column>
              </row>
            </actual>
          </old-data>
          <new-data>
            <expected count="0"/>
            <actual count="0"/>
          </new-data>
        </errors>
      </delta-assertion>
    </jdbdt-log-message>


## Contribute

The code is hosted at [GitHub](https://github.com/JDBDT/jdbdt).
Please use the [issue tracker](https://github.com/edrdo/JDBDT/issues)
to report bugs or propose new features. For other issues e-mail
`delta _at_ jdbdt.org`.

## Installation 

**Prerequisite:** JDBDT requires Java 8, it will not work 
with earlier Java versions. 

**Maven Central**

JDBDT is available from [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cjdbdt).

*Maven setup*

	<dependency>
		<groupId>org.jdbdt</groupId>
        <artifactId>jdbdt</artifactId>
        <version>1.1.3-SNAPSHOT</version>
    </dependency>

*Gradle setup*

     compile 'org.jdbdt:jdbdt:1.1.3-SNAPSHOT'

Setup instructions for other build systems are available [here](dependency-info.html) .
    
**GitHub**

JDBDT release artifacts are also available
at [GitHub](https://github.com/JDBDT/jdbdt/releases).

**Snapshot builds**

To compile and install the latest snapshot from scratch, use
the following commands:

	git clone git@github.com:JDBDT/jdbdt.git
	cd jdbdt
	mvn install 
