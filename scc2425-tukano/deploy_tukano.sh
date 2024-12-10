#!/bin/bash

# Set the script to exit immediately if any command fails
set -e

echo "Building the Maven package..."
mvn clean package

echo "Building the Docker image..."
docker build -t tukano-app:latest .

echo "Starting Minikube..."
minikube start

echo "Deleting existing deployments, services, and pods..."
kubectl delete deployments,services,pods --all --ignore-not-found

echo "Deleting persistent volumes and persistent volume claims..."
kubectl delete pvc --all --ignore-not-found
kubectl delete pv --all --ignore-not-found

echo "Loading Docker image into Minikube..."
minikube image load tukano-app:latest

echo "Applying Kubernetes configurations..."
kubectl apply -f k8s/tukano_webapp.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/storage-minio.yaml
kubectl apply -f k8s/redis.yaml

echo "Waiting for the service to stabilize..."
sleep 3

echo "Launching Tukano service in Minikube..."
minikube service tukano-service
