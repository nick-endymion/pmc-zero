#############  mfiles > media

select mtype, count(*) from mfiles group by mtype;

delete from a_media  where id in (select id from mfiles);

select * from mfiles where mtype = 0;
#   7964 undefined
select * from mfiles where mtype = 5;
# 586582  IMEDIUM
select * from mfiles where mtype is null;
#   2621  null > erroneous
insert into a_media(id,created_at, name, mtype)
select id, modified, substring_index(filename, '/', -1) , '5'  from mfiles
where mtype = 0 or mtype = 5 or mtype is null;
update a_media set name = substring_index(name, '?',1);

select * from mfiles where mtype = 1;
# 25 locations
insert into a_media(id,created_at, name, mtype)
select m.id, m.modified, 'to be updated', '1'  from mfiles m where mtype = 1;
update a_media m inner join locations l on l.mfile_id = m.id
set m.name = l.name where m.mtype = 1;

select * from mfiles where mtype = 6;
# 7597 Booksmarks
insert into a_media(id,created_at, name, mtype)
select m.id, m.modified, 'to be updated', '6'  from mfiles m where mtype = 6;
update a_media m inner join bookmarks b on b.mfile_id = m.id
set m.name = b.title where m.mtype = 6;

select * from mfiles where mtype = 8;
# 4492 folders
insert into a_media(id,created_at, name, mtype)
select m.id, m.modified, m.title, '8'  from mfiles m where mtype = 8;

# 609281 > total
