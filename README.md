<!--
       Copyright 2017 IBM Corp All Rights Reserved

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

This service manages a *stock portfolio*.  The data is backed by two **DB2** tables, communicated with
via *JDBC*.  The following operations are available:

`GET /` - gets summary data for all portfolios.

`POST /{owner}` - creates a new portfolio for the specified owner.

`GET /{owner}` - gets details for the specified owner.

`PUT /{owner}` - updates the portfolio for the specified owner (by adding a stock).

`DELETE /{owner}` - removes the portfolio for the specified owner.

All operations return *JSON*.  A *portfolio* object contains fields named *owner*, *total*, and *loyalty*,
plus an array of *stocks*.  A *stock* object contains fields named *symbol*, *shares*, *price*, *total*,
and *date*.  The only operation that takes any query params is the 'PUT' operation, which expects params
named *symbol* and *shares*.

For example, doing a `PUT http://localhost:9080/portfolio/John?symbol=IBM&shares=123` (against a freshly
created portfolio for *John*) would return *JSON* like `{"owner": "John", "total": 19120.35, "loyalty": "Bronze",
"stocks": [{"symbol": "IBM", "shares": 123, "price": 155.45, "total": 19120.35, "date": "2017-06-26"}]}`.

The above REST call would also add a row to the Stocks table via a SQL statement like `INSERT INTO Stock
(owner, symbol, shares, price, total, dateQuoted) VALUES ('John', 'IBM', 123, 155.45, 19120.35, '2017-06-26')`,
and would update the corresponding row in the Portfolio table via a SQL statement like
`UPDATE Portfolio SET total = 19120.35, loyalty = 'Bronze' WHERE owner = 'John'`.

The code should work with any *JDBC* provider.  It has been tested with **DB2** and with **Derby**.  Changing providers simply means updating the *Dockerfile* to copy the *JDBC* jar file into the Docker image, and updating the *server.xml* to reference it and specify and database-specific settings.  No *Java* code changes are necessary when changing *JDBC* providers.  The database can either be another pod in the same Kubernetes environment, or it can be running on "bare metal" in a traditional on-premises environment.  Endpoint and credential info is specified in the Kubernetes secret and made available as environment variables to the server.xml.  See the *deploy.yaml* for details.

### Prerequisites for ICP Deployment
 This project requires two secrets: `jwt` and `db2`.  You can get the DB2 values from inspecting your DB2 secrets.
  ```bash
  kubectl create secret generic jwt -n stock-trader --from-literal=audience=stock-trader --from-literal=issuer=http://stock-trader.ibm.com
  
  kubectl create secret generic db2 --from-literal=id=<DB2_USERNAME> --from-literal=pwd=<DB2_PASSWORD> 
  --from-literal=host=<DB2_SVC_NAME> --from-literal=port=50000 --from-literal=db=<TRADER_DB_NAME>
  
  # Example db2:
  kubectl create secret generic db2 --from-literal=id=db2inst1 --from-literal=pwd=db2inst1 
 --from-literal=host=trader-ibm-db2oltp-dev --from-literal=port=50000 --from-literal=db=trader
  ```
  
  You'll also need to enable login to the IBM Cloud Private internal Docker registry by following [these steps]
  (https://www.ibm.com/support/knowledgecenter/en/SSBS6K_2.1.0/manage_images/configuring_docker_cli.html).  Don't 
  forget to restart Docker after adding your cert.  On macOS you can restart Docker by running:
  ```bash
  osascript -e 'quit app "Docker"'
  open -a Docker
  ```
 
 ### Build and Deploy to ICP
To build `trader` clone this repo and run:
```bash
mvn package
docker build -t portfolio:latest -t <ICP_CLUSTER>.icp:8500/stock-trader/portfolio:latest .
docker tag portfolio:latest <ICP_CLUSTER>.icp:8500/stock-trader/portfolio:latest
docker push <ICP_CLUSTER>.icp:8500/stock-trader/portfolio:latest

kubectl apply -f manifests/
```

In practice this means you'll run something like:
```bash
docker build -t portfolio:latest -t mycluster.icp:8500/stock-trader/portfolio:latest .
docker tag portfolio:latest mycluster.icp:8500/stock-trader/portfolio:latest
docker push mycluster.icp:8500/stock-trader/portfolio:latest

kubectl --namespace stock-trader apply -f manifests/
```
