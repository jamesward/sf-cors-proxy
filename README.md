Salesforce CORS Proxy
---------------------

Deploy on Heroku (replace XXX with your Salesforce instance):

    heroku create
    heroku config:add PROXY_HOST="XXX.salesforce.com"
    git push heroku master

Then when using the Salesforce REST API simply use your new Heroku app domain name instead of the Salesforce one.
