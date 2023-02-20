# Bayes Java Dota Challlenge

This is the [task](TASK.md).

Any additional information about your solution goes here.

# How to run

### Run as a Spring boot

```sh
mvn spring-boot:run
```

### Run only test cases

```sh
mvn test
```

### Package the application as a JAR file

```sh
mvn clean package
```

## Note
* Mockito added as maven config for mocking service in test
* Added [CombatLogParserService.java](src%2Fmain%2Fjava%2Fgg%2Fbayes%2Fchallenge%2Fservice%2FCombatLogParserService.java) for parsing combat log file and save into database
* Added [MatchService.java](src%2Fmain%2Fjava%2Fgg%2Fbayes%2Fchallenge%2Fservice%2FMatchService.java) for finding Match Entity by Id
* Test cases coverage for APIs for testing considering success case 