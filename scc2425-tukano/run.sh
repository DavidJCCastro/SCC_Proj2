mvn clean package
docker build -t tukano-app:latest .
minikube start
kubectl delete deployments,services,pods --all
kubectl delete pv,pvc --all
minikube image load tukano-app:latest
kubectl apply -f k8s/tukano_webapp.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/storage-minio.yaml
kubectl apply -f k8s/redis.yaml
