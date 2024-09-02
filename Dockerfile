FROM maven:3.8.6-openjdk-18-slim as maven_builder
COPY . /workdir
WORKDIR /workdir
ARG BUILD_ARGS="clean package" \
    TEST=1
RUN mkdir -p ~/.m2 && cp -vf settings.xml ~/.m2 || true
RUN if [ "$TEST" -ne "1" ]; then export BUILD_ARGS="${BUILD_ARGS} -DskipTests"; fi && \
    echo ${BUILD_ARGS} && \
    mvn --batch-mode $BUILD_ARGS && \
    ls -la /workdir/target

FROM openjdk:18.0.2-jdk-oraclelinux8
#RUN adduser --system --user-group mdl
#USER mdl:mdl
COPY --from=maven_builder /workdir/target/mdl-*-jar-with-dependencies.jar /app/mdl.jar
ENTRYPOINT ["java","-jar","/app/mdl.jar"]
