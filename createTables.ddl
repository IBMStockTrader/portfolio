--       Copyright 2017 IBM Corp All Rights Reserved

--   Licensed under the Apache License, Version 2.0 (the "License");
--   you may not use this file except in compliance with the License.
--   You may obtain a copy of the License at

--       http://www.apache.org/licenses/LICENSE-2.0

--   Unless required by applicable law or agreed to in writing, software
--   distributed under the License is distributed on an "AS IS" BASIS,
--   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
--   See the License for the specific language governing permissions and
--   limitations under the License.

--   Run this via "db2 -tf createTables.ddl"

CONNECT TO Sample;
CREATE TABLE Portfolio(owner VARCHAR(32) NOT NULL, total DOUBLE, loyalty VARCHAR(8), PRIMARY KEY(owner));
CREATE TABLE Stock(owner VARCHAR(32) NOT NULL, symbol VARCHAR(8) NOT NULL, shares INTEGER, price DOUBLE, total DOUBLE, dateQuoted DATE, FOREIGN KEY (owner) REFERENCES Portfolio(owner) ON DELETE CASCADE, PRIMARY KEY(owner, symbol));
CONNECT RESET;
