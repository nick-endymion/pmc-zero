####################### mfiles/folders > bessources
select * from mfiles m where m.folder_id is null;
#  7622 # locations 0 and bookmarks 6 >  no bessource needed
select * from mfiles m, folders f where m.folder_id = f.id and m.filename is null;
#  4492  # dies sind alle vom type 8, also folders >  no bessource needed
select m.id, concat(f.mpath, f.lfolder, m.filename), f.storage_id, m.id from mfiles m, folders f
where m.folder_id = f.id and not m.filename like '---%';
# 52078
select m.id, concat(f.mpath, f.lfolder, m.filename), f.storage_id, m.id from mfiles m, folders f
where m.folder_id = f.id and m.filename like '---%';
# 545089
# 609281 > total

delete from a_bessources  where id in (select id from mfiles);
insert into a_bessources(id,name,storage_id, medium_id)
select m.id, concat(f.mpath, f.lfolder, m.filename), f.storage_id, m.id
from mfiles m, folders f where m.folder_id = f.id and not m.filename like '---%';
insert into a_bessources(id,name,storage_id, medium_id)
select m.id, concat(f.mpath, f.lfolder, substring(filename,5,length(filename)-9)), f.storage_id, m.id
from mfiles m, folders f where m.folder_id = f.id and m.filename like '---%';
update a_bessources set name = substring_index(name, '?',1);
