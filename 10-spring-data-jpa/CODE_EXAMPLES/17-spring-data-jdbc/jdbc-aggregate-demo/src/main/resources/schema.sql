-- Spring Data JDBC never generates tables. You write the schema (here, or with Flyway - topic 12).
create table customer (
    id   bigint auto_increment primary key,
    name varchar(100) not null
);

create table purchase_order (
    id          bigint auto_increment primary key,
    customer_id bigint not null references customer(id),
    status      varchar(20) not null
);

-- No id of its own: an item is identified by its order and its position in the order.
create table order_item (
    order_id bigint not null references purchase_order(id),
    line_no  int not null,
    sku      varchar(40) not null,
    quantity int not null,
    primary key (order_id, line_no)
);

create table product (
    sku  varchar(40) primary key,
    name varchar(100) not null
);
