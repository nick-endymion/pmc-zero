####################### storages  > storages
delete from a_storages where id in (select id from storages);
insert into a_storages(id,  name) SELECT id, name from storages;
