#/bin/bash

bash ./gradlew clean build -x test -x spotlessJavaCheck -x protobuf
