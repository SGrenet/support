# À propos de l'application Support

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Conseil Régional Nord Pas de Calais - Picardie, Conseil départemental de l'Essonne, Conseil régional d'Aquitaine-Limousin-Poitou-Charentes
* Développeur(s) : ATOS
* Financeur(s) : Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes
* Description : Application de gestion de tickets support internes à l'ENT avec gestion de l'escalade vers un service tiers comme Redmine. L'application permet à un utilisateur d'ouvrir et de suivre un ticket de support sur l'ENT. Un gestionnaire peut prendre en charge le ticket de support, le traiter ou l'escalader au support de niveau 2/3 directement dans l'ENT. Le ticket est alors transféré vers un service tiers automatiquement.

## Déployer dans ent-core
<pre>
		gradle clean install
</pre>

# Présentation du module

## Fonctionnalités

Aide & Support permet de signaler une difficulté ou un problème d'utilisation de l'ENT aux administrateurs de l'établissement.

Il met en œuvre un comportement de recherche sur le sujet et la description des demandes.

## Configuration
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
        "activate-escalation" : true,
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

        "activate-escalation" : booléen permettant d'activer / de désactiver l'escalade vers Redmine

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
