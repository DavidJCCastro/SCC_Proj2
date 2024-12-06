# Mudei o pom.xml, agora n usa o tomcat (n acho que vamos precisar por enquanto, a n ser no futuro para a parte do authentication).
# Tambem mudei mudei umas cenas no assembly plugin, especifiquei a mainClass (TukanoRestServer) e also agora da compile do jar como fatJar (jar-with-dependencies).
# Also no TukanoRestServer mudei o SERVER_BASE_URI para 0.0.0.0 (localhost) para poder aceder ao servidor a partir do meu pc, se não o gajo só podia ser acedido a partir do proprio container.

# Criei o Dockerfile

# Exec:

1. mvn clean package
2. docker build -t tukano-app:latest .  (tukano-app ou wtv que achares fitting).
3. docker run -it --rm -p 8080:8080 tukano-app:latest (isto para testar localmente se o docker esta a funcionar)

Já experimentei o REST API com Postman, e dá para dar e post e get de users.

---------------------
Commands:

minikube start 

Loading a docker image to minikube docker environment:

minikube image load <image-name>
minikube image load tukano-app:latest

Applying the YAML files (deploying the app using YAML files):

kubectl apply -f <path-to-yaml-file>
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/tukano_webapp.yaml
kubectl apply -f k8s/redis.yaml

Getting the service URL (URL to access the app):

minikube service <service-name>  (specified in the service.yaml)
minikube service tukano-service

Debugging:

Checking Pods status:

kubectl get pods

Checking the logs of a specific Pod:

kubectl logs <pod-name>


------
kubectl delete pod $(kubectl get pods -o jsonpath='{.items[0].metadata.name}')
kubectl delete pod $(kubectl get pods -o jsonpath='{.items[1].metadata.name}')
Este comando apaga um pod (na pos [0]) e o kubernetes vai criar um novo automaticamente. Basicamente dá restart no pod (util para testar a persistence).

kubectl delete pod <app-pod-name>
kubectl delete pod tukano-app-7459f4f567-9wgzp

kubectl delete deployment tukano-app

Delete the pods, services, and deployments:

kubectl delete deployments,services,pods -all

Delete persistent volumes:

kubectl delete pv --all


------

REST Requests para testar:

POST http://192.168.49.2:32122/rest/users
{
    "userId": "wiirijo",
    "pwd": "easypass",
    "email": "wiirijo@fct.unl.pt",
    "displayName": "wiirijo"
}

GET http://192.168.49.2:32122/rest/users/wiirijo?pwd=easypass

PUT http://192.168.49.2:32122/rest/users/wiirijo?pwd=easypass
{
    "userId": "wiirijo",
    "pwd": "123456",
    "email": "wiirijo@fct.unl.pt",
    "displayName": "wiirijo"
}

GET http://192.168.49.2:32122/rest/users/wiirijo?pwd=123456

DELETE http://192.168.49.2:32122/rest/users/wiirijo?pwd=123456

-----

Criar a DB PostgreSQL Dockerized

Correr a PostgreSQL localmente:

docker run --name <container-name> -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=secret -e POSTGRES_DB=tukano -p 5432:5432 -d postgres:latest

-e POSTGRES_USER=postgres # username para a db
-e POSTGRES_PASSWORD=secret # pwd para a db
-e POSTGRES_DB=tukano # nome da db
-p 5432:5432 # Dá map do port 5432 (default port do postgreSQL) do nosso PC para o port 5432 dentro do container. Isto torna a db acessível a partir do nosso PC em (localhost:5432)
-d # flag para correr o container em detached mode (background)
postgres:latest # imagem usada para o container. Neste caso official PostgreSQL Docker image

docker run --name postgres-db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=secret -e POSTGRES_DB=tukano -p 5432:5432 -d postgres:latest

docker rm -f <container-name>
docker rm -f postgres-db


changes no hibernate cfg:

troquei isto:
<!-- JDBC Database connection settings -->
		<property name="connection.driver_class">org.postgresql.Driver</property>
		<property name="connection.url">jdbc:postgresql://postgres-service:5432/tukano</property>
		<property name="connection.username">postgres</property>
		<property name="connection.password">secret</property>
<!-- Dá match com o postgre service agora -->

<!-- Schema generation strategy -->
		<property name="hbm2ddl.auto">update</property>
<!-- Garante que a db schema seja automatically updated com base nas entidades mapped -->

<!-- Database dialect -->
		<property name="dialect">org.hibernate.dialect.PostgreSQLDialect</property>
<!-- PostgreSQL-specific dialect for hibernate -->

Adding the postgres yaml configs:

kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/postgres-service.yaml


Minikube é um ganda mongoloide, e da cache de docker images, os comandos abaixo podem ser uteis qnd o gajo estiver a dar pull de uma imagem outdated (it happened, e demorei demasiado tempo figuring out...)

minikube ssh
docker rmi tukano-app:latest
exit

minikube image load tukano-app:latest

Com isto so far, já dá para enviar rest requests para o tukano-service e os dados ficam guardados no pod do postgres. Isto significa que se por exemplo der restart no pod da app, e tentar dar get de um user que foi criado antes, vai dar nike. Mas se der restart no pod do postgres dá barraca.

Also estes comandos vão ser uteis para testar a db:

Abrir o pod do postgres num internal terminal / shell:

kubectl exec -it <postgres-pod-name> -- /bin/bash

Usar o postgresql client para conectar a db:

psql -U postgres -d tukano
(pass : secret)

\dt # lista as tabelas existentes na db 

probably há mais comandos bacanos, gotta figure that out later:
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';

SET search_path TO <schema_name>;
\dt

...

Persistent Volume para postgres:

Persistent Volume (PV) representa a actual storage (fisica ou cloud). Pode ser considerado como o physical storage resource (ex: disco ou partição).

Persistent Volume Claim (PVC) é o intermediario entre os Pods e os PVs. Dá bind de um pod ao um PV com base nos modes de acesso ou tamanho.
Pode ser visto com um pedido ou uma reserva de storage.


Okay supostamente o minikube tem uma StorageClass built-in (usa o hostPath) que é usada para dynamic provisioning. Isto significa que em teoria não precisamos de definir o PV, só o PVC. A duvida é, será que o mesmo se aplica ao azure? no lab10 os stores só dão exemplo do PVC prtt idk (vou assumir que só precisamos de PVC devido ao dynamic provisioning).


No lab10:
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: azure-managed-disk
spec:
  accessModes:
  - ReadWriteOnce
  storageClassName: azurefile
  resources:
    requests:
      storage: 1Gi


storageClassName: azurefile # Isto diz aos Kubernetes para usar o azurefile StorageClass para criar PVs dinamicamente no Azure File Storage. Prtt qnd for para portar para azure temos de dar add disso. Minikube ñ precisa dessas merdas.

Aplicar a nova config:

kubectl apply -f k8s/postgres-pvc.yaml

Dar update na config do deployment do postgres:

kubectl apply -f k8s/postgres-deployment.yaml

Check se esta a bombar:

kubectl get pvc


Restart no pod do postgres e em teoria PV para DB esta funcional.

Para testar:
Criar um user com o REST API, dar get só para confirmar.
Apagar o Pod do Postegres:
kubectl delete pod <postgres-pod-name>
Esperar um bit e tentar dar get do user.


----todos os deploys

mvn clean package
docker build -t tukano-app:latest .
minikube start
kubectl delete deployments,services,pods --all
kubectl delete pv,pvc --all
-----
minikube ssh

docker rmi tukano-app:latest
exit
----
minikube image load tukano-app:latest
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/postgres-pvc.yaml
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/postgres-service.yaml
kubectl apply -f k8s/minio-pvc.yaml
kubectl apply -f k8s/minio-deployment.yaml
kubectl apply -f k8s/minio-service.yaml

if needed:
minikube service tukano-service

xrandr --output HDMI-0 --scale 0.75x0.75


------

Right now os secrets estão hardcoded nos yamls. Por enquanto isso n faz diferença nenhuma. Mas num futuro sq fazemos isto:

Exemplo para o minio:

Em vez de ter isto:
env:
...
- name: S3_ENDPOINT
  value: "http://minio-service:9000"
- name: S3_ACCESS_KEY
  value: "minioadmin"
- name: S3_SECRET_KEY
  value: "minioadmin"
- name: S3_BUCKET
  value: "tukano-blobs"
...

ter isto:
env:
...
- name: S3_ENDPOINT
  value: "http://minio-service:9000"
- name: S3_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: s3-credentials
      key: S3_ACCESS_KEY
- name: S3_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: s3-credentials
      key: S3_SECRET_KEY
- name: S3_BUCKET
  value: "tukano-blobs"
  ...

  e dar set dos secrets assim:
  kubectl create secret generic s3-credentials \
  --from-literal=S3_ACCESS_KEY=minioadmin \
  --from-literal=S3_SECRET_KEY=minioadmin


------

Tip para visualização da storage.

Após o deployment e aplicação das configs, recomendo dar um kubectl get service , pods, etc só para checkar se está tudo normal.

Dps do kubectl get pods, dar copy no nome do minio-deployment e fazer:

kubectl logs <minio-deployment>
o output vai ser algo do genero:
INFO: WARNING: MINIO_ACCESS_KEY and MINIO_SECRET_KEY are deprecated.
         Please use MINIO_ROOT_USER and MINIO_ROOT_PASSWORD
MinIO Object Storage Server
Copyright: 2015-2024 MinIO, Inc.
License: GNU AGPLv3 - https://www.gnu.org/licenses/agpl-3.0.html
Version: RELEASE.2024-11-07T00-52-20Z (go1.23.3 linux/amd64)

API: http://10.244.0.119:9000  http://127.0.0.1:9000 
WebUI: http://10.244.0.119:36721 http://127.0.0.1:36721   

Docs: https://docs.min.io
WARN: Detected default credentials 'minioadmin:minioadmin', we recommend that you change these values with 'MINIO_ROOT_USER' and 'MINIO_ROOT_PASSWORD' environment variables

O importante é esta linha:
WebUI: http://10.244.0.119:36721 http://127.0.0.1:36721   
Isto aqui diz o port e o ip para aceder ao webUI dentro do cluster, (meaning que este port "36721" está mapped dentro do cluster) então para aceder a esse webUI fora do cluster é necessário expor o port para a nossa maquina.

Isso é feito no minio-service.yaml:

 ports:
    - protocol: TCP
      port: 9000
      targetPort: 9000
      nodePort: 30000
      name: api-port
    - protocol: TCP
      targetPort: 36721  <---- já troquei aqui
      port: 36721        <----
      nodePort: 30001    <---- E aqui estou a dizer que posso me conectar ao (36721) a partir do port 30001 na minha máquina
      name: web-ui-port
  type: NodePort

  Dar save deste file e fazer:
  kubectl apply -f k8s/minio-service.yaml

  Finalmente:
  minikube service minio-service --url

  output:
  http://192.168.49.2:30000   <---- Podes mandar pedidos a api a partir deste IP (não vai ser necessário, mas ta ai)
  http://192.168.49.2:30001   <---- Abrir o webUI na maquina local (MinIO Console)

  Dps é só dar login com as credencias do minio-deployment.yaml
  username: minioadmin
  password: minioadmin

O UI é actually fire

Se for só dar um check básico para ver se esta a funcionar podes fazer só:

kubectl exec -it <minio-deployment-pod> -- ls -la /data/tukano-blobs