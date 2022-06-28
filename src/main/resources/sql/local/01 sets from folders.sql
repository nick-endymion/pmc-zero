#######################   folder > set including medium_id (from mfile_id)
insert into a_sets(id, medium_id, name)
select id, mfile_id, title from folders;
