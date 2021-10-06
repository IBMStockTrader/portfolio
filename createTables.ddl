--       Copyright 2017-2021 IBM Corp All Rights Reserved

--   Licensed under the Apache License, Version 2.0 (the "License");
--   you may not use this file except in compliance with the License.
--   You may obtain a copy of the License at

--       http://www.apache.org/licenses/LICENSE-2.0

--   Unless required by applicable law or agreed to in writing, software
--   distributed under the License is distributed on an "AS IS" BASIS,
--   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
--   See the License for the specific language governing permissions and
--   limitations under the License.

--   Run this via "db2 -tf createTables.ddl" on DB2, or via psql on PostgreSQL

CREATE TABLE Portfolio(owner VARCHAR(32) NOT NULL, total DOUBLE PRECISION, accountID VARCHAR(64), PRIMARY KEY(owner));
CREATE TABLE Stock(owner VARCHAR(32) NOT NULL, symbol VARCHAR(8) NOT NULL, shares INTEGER, price DOUBLE PRECISION, total DOUBLE PRECISION, dateQuoted VARCHAR(10), commission DOUBLE PRECISION, FOREIGN KEY (owner) REFERENCES Portfolio(owner) ON DELETE CASCADE, PRIMARY KEY(owner, symbol));
