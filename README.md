# À propos de l'application Support

* licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt)
* financeur : Région Picardie, Conseil général  91, Région Poitou Charente
* description : Application de gestion de tickets internes à l'ENT avec escalade vers Redmine

<pre>
		gradle copyMod
</pre>

## Déployer dans ent-core
Contenu du fichier deployment/support/conf.json.template :
    {
      "name": "net.atos~support~0.3-SNAPSHOT",
      "config": {
        "main" : "net.atos.entng.support.Support",
        "port" : 8027,
        "sql" : true,
        "mongodb" : false,
        "neo4j" : false,
        "app-name" : "Aide et support",
        "app-address" : "/support",
        "app-icon" : "support-large",
        "host": "${host}",
        "ssl" : $ssl,
        "auto-redeploy": false,
        "userbook-host": "${host}",
        "integration-mode" : "HTTP",
        "mode" : "${mode}",
        "bug-tracker-host" : "support.web-education.net",
        "bug-tracker-port" : 80,
        "bug-tracker-api-key" : "keyExample1234",
        "bug-tracker-projectid" : 39,
        "bug-tracker-resolved-statusid" : 3,
        "bug-tracker-closed-statusid" : 5,
        "escalation-httpclient-maxpoolsize" : 16,
        "escalation-httpclient-keepalive" : false,
        "escalation-httpclient-tryusecompression" : true,
        "refresh-period" : 15
      }
    }

Les paramètres spécifiques à l'application support sont les suivants :

        "bug-tracker-host" : hostname du serveur hébergeant Redmine
        "bug-tracker-port" : port du serveur hébergeant Redmine
        "bug-tracker-api-key" : clé associée au compte Redmine utilisé pour l'escalade. Elle permet de faire des appels REST. Cf http://www.redmine.org/projects/redmine/wiki/Rest_api#Authentication
        "bug-tracker-projectid" : identifiant du projet Redmine
        "bug-tracker-resolved-statusid" : entier correspondant au statut "Résolu" dans Redmine
        "bug-tracker-closed-statusid" : entier correspondant au statut "Fermé" dans Redmine
        "refresh-period" : période de rafraîchissement en minutes. L'ENT récupère les données de Redmine et les sauvegarde toutes les "refresh-period" minutes

        "escalation-httpclient-maxpoolsize" : paramètre "maxpoolsize" du client HTTP vert.x utilisé par le module Support pour communiquer avec Redmine en REST
        "escalation-httpclient-keepalive" : paramètre "keepalive" du client HTTP vert.x utilisé par le module Support pour communiquer avec Redmine en REST
        "escalation-httpclient-tryusecompression" : paramètre "tryusecompression" du client HTTP Vert.x utilisé par le module Support pour communiquer avec Redmine en REST

        Se reporter à la javadoc du client HTTP vert.x pour le détail des paramètres : http://vertx.io/api/java/org/vertx/java/core/http/HttpClient.html


## Configuration
TODO
