-- Exercise 10 testbed: four tables, joined two, three and four at a time.
-- Run from the repository root: ./engine -f src/test/resources/joins/joins.sql
-- In DuckDB, add (HEADER false) to each COPY: customers.csv has only STRING columns, so
-- DuckDB's sniffer would take its first line for a header and drop a row.
CREATE TABLE orders (o_orderkey LONG, o_custname STRING, o_prodname STRING, o_quantity LONG);
CREATE TABLE products (p_name STRING, p_price DOUBLE);
CREATE TABLE customers (c_name STRING, c_city STRING);
CREATE TABLE cities (ct_city STRING, ct_country STRING, ct_population LONG);
COPY orders FROM 'src/test/resources/joins/orders.csv';
COPY products FROM 'src/test/resources/joins/products.csv';
COPY customers FROM 'src/test/resources/joins/customers.csv';
COPY cities FROM 'src/test/resources/joins/cities.csv';
SELECT * FROM orders, customers WHERE o_custname = c_name;
SELECT * FROM orders, customers, products
WHERE o_custname = c_name AND o_prodname = p_name;
SELECT * FROM orders, customers, products, cities
WHERE o_custname = c_name AND o_prodname = p_name AND c_city = ct_city;
SELECT * FROM orders, customers, products
WHERE o_custname = c_name AND o_prodname = p_name AND c_city = 'Copenhagen';
