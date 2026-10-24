drop policy if exists classrooms_delete_owner on public.classrooms;
revoke delete on public.classrooms from authenticated;

create or replace function public.delete_classroom(p_classroom_id uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, pg_temp
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentication is required';
    end if;

    delete from public.classrooms
    where id = p_classroom_id and teacher_id = auth.uid();

    if not found then
        raise exception 'Classroom was not found or is not owned by this account';
    end if;
end;
$$;

revoke all on function public.delete_classroom(uuid) from public, anon;
grant execute on function public.delete_classroom(uuid) to authenticated;
