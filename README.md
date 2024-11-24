# Mudei o pom.xml, agora n usa o tomcat (n acho que vamos precisar por enquanto, a n ser no futuro para a parte do authentication).
# Tambem mudei mudei umas cenas no assembly plugin, especifiquei a mainClass (TukanoRestServer) e also agora da compile do jar como fatJar (jar-with-dependencies).
# Also no TukanoRestServer mudei o SERVER_BASE_URI para 0.0.0.0 (localhost) para poder aceder ao servidor a partir do meu pc, se não o gajo só podia ser acedido a partir do proprio container.

# Criei o Dockerfile

# Exec:

1. mvn clean package
2. docker build -t tukano-app:latest .  (tukano-app ou wtv que achares fitting).
3. docker run -it --rm -p 8080:8080 tukano-app:latest

Já experimentei o REST API com Postman, e dá para dar e post e get de users.
