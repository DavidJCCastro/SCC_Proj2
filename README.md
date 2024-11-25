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
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

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

