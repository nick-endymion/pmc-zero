#######################   folder > set including medium_id (from mfile_id)
delete from a_sets  where id in (select id from folders);
insert into a_sets(id, medium_id, name)
select id, mfile_id, title from folders;
