import { useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Field, FormDialog, Values } from '../../components/ui/FormDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

export interface Binding { type: string; target: string; value?: string | number }
export interface Option { id: number; groupId: number; code: string; name: string; description?: string; imageUrl?: string; priceDelta: number; renderBinding: Binding | null; sortOrder: number; active: boolean }
export interface Group { id: number; code: string; name: string; description?: string; sortOrder: number; active: boolean; options: Option[] }
const BINDING_TYPES = [['', 'None (no 3D/2D change yet)'], ['mesh_toggle', 'Show/hide a model part'], ['material_color', 'Change a material colour'], ['texture', 'Swap a texture'], ['morph_target', 'Blend a shape'], ['layer_2d', '2D preview layer']]

export default function AdminCustomization() {
  const { data, loading, error, reload } = useAsync(() => get<Group[]>('/admin/customization/groups'), [])
  const [groupDlg, setGroupDlg] = useState<{ open: boolean; g?: Group }>({ open: false })
  const [optDlg, setOptDlg] = useState<{ open: boolean; group?: Group; o?: Option }>({ open: false })
  const [del, setDel] = useState<{ kind: 'groups' | 'options'; id: number; name: string } | null>(null)

  const groupFields: Field[] = [{ name: 'name', label: 'Name', required: true, hint: 'Shown to customers, e.g. Collar type' }, { name: 'code', label: 'Code', required: true, hint: 'lowercase_with_underscores, e.g. collar_type' },
    { name: 'description', label: 'Description', type: 'textarea' }, { name: 'sortOrder', label: 'Sort order', type: 'number' }, { name: 'active', label: 'Available', type: 'checkbox' }]
  const optFields: Field[] = [{ name: 'name', label: 'Name', required: true }, { name: 'code', label: 'Code', required: true, hint: 'lowercase_with_underscores' }, { name: 'description', label: 'Description', type: 'textarea' },
    { name: 'imageUrl', label: 'Picture shown to customers', type: 'image' }, { name: 'priceDelta', label: 'Extra price (₹)', type: 'number', step: '0.01', required: true },
    { name: 'bType', label: 'What it changes in the preview', type: 'select', options: BINDING_TYPES.map(([value, label]) => ({ value, label })) },
    { name: 'bTarget', label: 'Preview target name', hint: 'Use the exact Blender node name (e.g. collar_spread, pocket_patch, sleeve_long; sleeve_long_l/r also match), material name (e.g. fabric, button), or shape-key name (e.g. fit_slim).' }, { name: 'bValue', label: 'Preview value (optional)', hint: 'Colour: #1F3A5F (empty uses selected fabric colour). Texture/2D layer: /media/... image URL. Shape: 0..1. Part toggle: leave empty.' },
    { name: 'sortOrder', label: 'Sort order', type: 'number' }, { name: 'active', label: 'Available', type: 'checkbox' }]

  const saveGroup = async (v: Values) => {
    const body = { name: v.name, code: v.code, description: v.description || null, sortOrder: Number(v.sortOrder || 0), active: v.active }
    if (groupDlg.g) await http.put(`/admin/customization/groups/${groupDlg.g.id}`, body); else await post('/admin/customization/groups', body)
    toast.success('Group saved.'); setGroupDlg({ open: false }); reload()
  }
  const saveOption = async (v: Values) => {
    const renderBinding = v.bType ? { type: v.bType, target: v.bTarget, ...(v.bValue ? { value: v.bValue } : {}) } : null
    const body = { groupId: optDlg.group!.id, code: v.code, name: v.name, description: v.description || null, imageUrl: v.imageUrl || null, priceDelta: Number(v.priceDelta), renderBinding, sortOrder: Number(v.sortOrder || 0), active: v.active }
    if (optDlg.o) await http.put(`/admin/customization/options/${optDlg.o.id}`, body); else await post('/admin/customization/options', body)
    toast.success('Option saved.'); setOptDlg({ open: false }); reload()
  }
  const remove = async () => {
    try { await http.delete(`/admin/customization/${del!.kind}/${del!.id}`); toast.success('Deleted.'); reload() } catch (e) { toast.error((e as ApiError).message) }
    setDel(null)
  }
  const o = optDlg.o
  const optInitial: Values = o ? { name: o.name, code: o.code, description: o.description ?? '', imageUrl: o.imageUrl ?? '', priceDelta: String(o.priceDelta), bType: o.renderBinding?.type ?? '', bTarget: o.renderBinding?.target ?? '', bValue: o.renderBinding?.value != null ? String(o.renderBinding.value) : '', sortOrder: String(o.sortOrder), active: o.active }
    : { name: '', code: '', description: '', imageUrl: '', priceDelta: '0', bType: '', bTarget: '', bValue: '', sortOrder: '0', active: true }
  const g = groupDlg.g

  if (error) return <EmptyState title="Could not load customization options" hint={error.message} />
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Customization options</h1><Button onClick={() => setGroupDlg({ open: true })}>Add group</Button></div>
      <p className="mt-1 text-sm">Groups (Collar type, Sleeve length…) hold the choices customers see. Attach groups and options to each product on its edit page.</p>
      <div className="mt-6 space-y-4">
        {loading ? <Skeleton className="h-48" /> : data!.length === 0 ? <EmptyState title="No groups yet" hint="Start with a group such as “Collar type”, then add its options." /> : data!.map((grp) => (
          <section key={grp.id} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
            <div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className="text-lg font-bold">{grp.name}{!grp.active && <span className="ml-2 text-xs font-normal">(hidden)</span>}</h2><div className="text-xs">{grp.code} · {grp.options.length} option{grp.options.length === 1 ? '' : 's'}</div></div>
              <div className="flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={() => setOptDlg({ open: true, group: grp })}>+ Option</button><button className="text-primary" onClick={() => setGroupDlg({ open: true, g: grp })}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={() => setDel({ kind: 'groups', id: grp.id, name: grp.name })}>Delete</button></div></div>
            {grp.options.length > 0 && <ul className="mt-3 divide-y divide-ink/10 border-t border-ink/10">{grp.options.map((op) => (
              <li key={op.id} className="flex flex-wrap items-center justify-between gap-2 py-2"><div className="flex items-center gap-3">{op.imageUrl && <img src={op.imageUrl} alt="" className="h-9 w-9 rounded object-cover" />}
                <div><span className="font-semibold text-ink">{op.name}</span>{!op.active && <span className="ml-2 text-xs">(hidden)</span>}<div className="text-xs">+₹{Number(op.priceDelta).toFixed(2)} · {op.renderBinding ? `${op.renderBinding.type} → ${op.renderBinding.target}` : 'no preview change'}</div></div></div>
                <div className="flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={() => setOptDlg({ open: true, group: grp, o: op })}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={() => setDel({ kind: 'options', id: op.id, name: op.name })}>Delete</button></div></li>))}</ul>}
          </section>))}
      </div>
      <FormDialog open={groupDlg.open} title={g ? 'Edit group' : 'New group'} fields={groupFields} initial={g ? { name: g.name, code: g.code, description: g.description ?? '', sortOrder: String(g.sortOrder), active: g.active } : { name: '', code: '', description: '', sortOrder: '0', active: true }} onSubmit={saveGroup} onCancel={() => setGroupDlg({ open: false })} />
      <FormDialog open={optDlg.open} title={`${o ? 'Edit' : 'New'} option${optDlg.group ? ` in ${optDlg.group.name}` : ''}`} fields={optFields} initial={optInitial} onSubmit={saveOption} onCancel={() => setOptDlg({ open: false })} />
      <ConfirmDialog open={!!del} danger title={`Delete “${del?.name}”?`} message={del?.kind === 'groups' ? 'Delete the group’s options first. Groups used by products cannot be deleted.' : 'Products stop offering this option immediately.'} confirmLabel="Delete" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
