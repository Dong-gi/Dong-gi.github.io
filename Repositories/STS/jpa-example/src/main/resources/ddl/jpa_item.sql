create table jpa_item1 (
    item_id bigserial primary key,
    price int not null,
    discount int
);
create table jpa_item2 (
    item_id bigserial primary key,
    ko_price int not null,
    ko_discount int,
    jp_price int not null,
    jp_discount int
);
create table jpa_item3 (
    item_id bigserial primary key,
    new_price int not null,
    new_discount int,
    old_price int not null,
    old_discount int
);