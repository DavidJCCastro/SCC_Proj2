# Creating the database
az container create \
  --resource-group k8s \
  --name postgres \
  --image postgres:latest \
  --ports 5432 \
  --dns-name-label postgres \
  --environment-variables POSTGRES_DB=tukano POSTGRES_USER=postgres POSTGRES_PASSWORD=secret

# Creating the redis cache
az container create \
  --resource-group k8s \
  --name redis \
  --image redis:latest \
  --ports 6379 \
  --dns-name-label redis



# Creating the blobStorage persistent volume
az container create --resource-group k8s --name tukano-webapp --image djccastro/tukano-app --ports 5432 <list_of_ports> --dns-name-label <desired_dns_prefix>


az container create --resource-group k8s --name tukano-webapp --image djccastro/tukano-app --ports 5432 <list_of_ports> --dns-name-label <desired_dns_prefix>
