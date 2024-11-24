# Step 1: Use a lightweight Java runtime as the base image
FROM openjdk:17-jdk-slim

# Step 2: Set the working directory inside the container
WORKDIR /app

# Step 3: Copy the app compiled JAR file to the container
COPY target/tukano-1-jar-with-dependencies.jar /app/tukano.jar

# Step 4: Copy configuration files to the container (idk if its necessary)
COPY hibernate.cfg.xml /app/hibernate.cfg.xml

# Step 5: Expose the app port 
EXPOSE 8080

# Step 6: Define the default command to run the app
CMD ["java", "-jar", "tukano.jar"]
