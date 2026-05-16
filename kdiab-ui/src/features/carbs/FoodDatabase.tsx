import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { carbsApi } from '../../api/carbsApi'
import type { FoodEntryResponse } from '../../api/carbsApi'
import { ConfirmModal } from '../../components/ConfirmModal'

interface FoodDatabaseProps {
  userId: string
}

interface FoodForm {
  name: string
  portionGrams: string
  carbsPer100g: string
}

const emptyForm = (): FoodForm => ({ name: '', portionGrams: '', carbsPer100g: '' })

const inputStyle: React.CSSProperties = {
  padding: '6px 8px',
  border: '1px solid var(--border-color)',
  borderRadius: '4px',
  fontSize: '0.9rem',
  width: '100%',
  boxSizing: 'border-box',
}

const cellInputStyle: React.CSSProperties = {
  ...inputStyle,
  width: '90%',
}

export const FoodDatabase: React.FC<FoodDatabaseProps> = ({ userId }) => {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [showAddForm, setShowAddForm] = useState(false)
  const [addForm, setAddForm] = useState<FoodForm>(emptyForm())
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editForm, setEditForm] = useState<FoodForm>(emptyForm())
  const [validationError, setValidationError] = useState<string | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['foods', userId],
    queryFn: () => carbsApi.listFoods(userId),
    select: (res) => res.data,
    enabled: !!userId,
  })

  const createMutation = useMutation({
    mutationFn: (body: { name: string; portionGrams: number; carbsPer100g: number }) =>
      carbsApi.createFood(userId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['foods', userId] })
      setShowAddForm(false)
      setAddForm(emptyForm())
      setValidationError(null)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ foodId, body }: { foodId: string; body: { name: string; portionGrams: number; carbsPer100g: number } }) =>
      carbsApi.updateFood(userId, foodId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['foods', userId] })
      setEditingId(null)
      setEditForm(emptyForm())
      setValidationError(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (foodId: string) => carbsApi.deleteFood(userId, foodId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['foods', userId] })
    },
  })

  const validateForm = (form: FoodForm): boolean => {
    const portion = parseFloat(form.portionGrams)
    const carbs = parseFloat(form.carbsPer100g)
    if (!form.name.trim() || isNaN(portion) || portion < 0 || isNaN(carbs) || carbs < 0 || carbs > 100) {
      setValidationError(t('foodDatabase.validationError'))
      return false
    }
    setValidationError(null)
    return true
  }

  const handleAddSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!validateForm(addForm)) return
    createMutation.mutate({
      name: addForm.name.trim(),
      portionGrams: parseFloat(addForm.portionGrams),
      carbsPer100g: parseFloat(addForm.carbsPer100g),
    })
  }

  const handleEditSubmit = (e: React.FormEvent, foodId: string) => {
    e.preventDefault()
    if (!validateForm(editForm)) return
    updateMutation.mutate({
      foodId,
      body: {
        name: editForm.name.trim(),
        portionGrams: parseFloat(editForm.portionGrams),
        carbsPer100g: parseFloat(editForm.carbsPer100g),
      },
    })
  }

  const handleDelete = (food: FoodEntryResponse) => {
    setDeleteConfirmId(food.id)
  }

  const startEdit = (food: FoodEntryResponse) => {
    setEditingId(food.id)
    setEditForm({
      name: food.name,
      portionGrams: String(food.portionGrams),
      carbsPer100g: String(food.carbsPer100g),
    })
    setValidationError(null)
  }

  if (isLoading) {
    return <p style={{ color: 'var(--text-secondary)' }}>{t('foodDatabase.loading')}</p>
  }

  if (isError) {
    return <p style={{ color: 'var(--color-danger, red)' }}>{t('foodDatabase.error')}</p>
  }

  const items = data?.items ?? []

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2 style={{ margin: 0 }}>{t('foodDatabase.title')}</h2>
        {!showAddForm && (
          <button className="primary" onClick={() => { setShowAddForm(true); setValidationError(null) }}>
            {t('foodDatabase.addFood')}
          </button>
        )}
      </div>

      {validationError && (
        <div role="alert" className="error" style={{ marginBottom: '1rem' }}>
          {validationError}
        </div>
      )}

      {showAddForm && (
        <form
          onSubmit={handleAddSubmit}
          style={{
            display: 'grid',
            gridTemplateColumns: '2fr 1fr 1fr 1fr auto auto',
            gap: '0.5rem',
            alignItems: 'center',
            marginBottom: '1rem',
            padding: '0.75rem',
            border: '1px solid var(--border-color)',
            borderRadius: '6px',
            background: 'var(--bg-surface, #f9f9f9)',
          }}
        >
          <input
            type="text"
            placeholder={t('foodDatabase.namePlaceholder')}
            value={addForm.name}
            onChange={(e) => setAddForm({ ...addForm, name: e.target.value })}
            style={inputStyle}
            autoFocus
            required
          />
          <input
            type="number"
            min="0"
            step="0.1"
            placeholder={t('foodDatabase.portionGrams')}
            value={addForm.portionGrams}
            onChange={(e) => setAddForm({ ...addForm, portionGrams: e.target.value })}
            style={inputStyle}
            required
          />
          <input
            type="number"
            min="0"
            max="100"
            step="0.1"
            placeholder={t('foodDatabase.carbsPer100g')}
            value={addForm.carbsPer100g}
            onChange={(e) => setAddForm({ ...addForm, carbsPer100g: e.target.value })}
            style={inputStyle}
            required
          />
          <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', textAlign: 'center' }}>
            {addForm.portionGrams && addForm.carbsPer100g
              ? `${((parseFloat(addForm.portionGrams) * parseFloat(addForm.carbsPer100g)) / 100).toFixed(1)}g`
              : '—'}
          </span>
          <button type="submit" className="primary" disabled={createMutation.isPending}>
            {t('foodDatabase.save')}
          </button>
          <button
            type="button"
            onClick={() => { setShowAddForm(false); setAddForm(emptyForm()); setValidationError(null) }}
          >
            {t('foodDatabase.cancel')}
          </button>
        </form>
      )}

      {items.length === 0 ? (
        <p style={{ color: 'var(--text-secondary)' }}>{t('foodDatabase.empty')}</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid var(--border-color)' }}>
              <th style={{ textAlign: 'left', padding: '0.5rem' }}>{t('foodDatabase.name')}</th>
              <th style={{ textAlign: 'right', padding: '0.5rem' }}>{t('foodDatabase.portionGrams')}</th>
              <th style={{ textAlign: 'right', padding: '0.5rem' }}>{t('foodDatabase.carbsPer100g')}</th>
              <th style={{ textAlign: 'right', padding: '0.5rem' }}>{t('foodDatabase.carbsForPortion')}</th>
              <th style={{ textAlign: 'center', padding: '0.5rem' }}>{t('list.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((food) =>
              editingId === food.id ? (
                <tr key={food.id} style={{ borderBottom: '1px solid var(--border-color)', background: 'var(--bg-surface, #f9f9f9)' }}>
                  <td style={{ padding: '0.4rem 0.5rem' }}>
                    <form id={`edit-form-${food.id}`} onSubmit={(e) => handleEditSubmit(e, food.id)} />
                    <input
                      form={`edit-form-${food.id}`}
                      type="text"
                      value={editForm.name}
                      onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                      style={cellInputStyle}
                      autoFocus
                      required
                    />
                  </td>
                  <td style={{ padding: '0.4rem 0.5rem', textAlign: 'right' }}>
                    <input
                      form={`edit-form-${food.id}`}
                      type="number"
                      min="0"
                      step="0.1"
                      value={editForm.portionGrams}
                      onChange={(e) => setEditForm({ ...editForm, portionGrams: e.target.value })}
                      style={cellInputStyle}
                      required
                    />
                  </td>
                  <td style={{ padding: '0.4rem 0.5rem', textAlign: 'right' }}>
                    <input
                      form={`edit-form-${food.id}`}
                      type="number"
                      min="0"
                      max="100"
                      step="0.1"
                      value={editForm.carbsPer100g}
                      onChange={(e) => setEditForm({ ...editForm, carbsPer100g: e.target.value })}
                      style={cellInputStyle}
                      required
                    />
                  </td>
                  <td style={{ padding: '0.4rem 0.5rem', textAlign: 'right', color: 'var(--text-secondary)' }}>
                    {editForm.portionGrams && editForm.carbsPer100g
                      ? `${((parseFloat(editForm.portionGrams) * parseFloat(editForm.carbsPer100g)) / 100).toFixed(1)}g`
                      : '—'}
                  </td>
                  <td style={{ padding: '0.4rem 0.5rem', textAlign: 'center', display: 'flex', gap: '0.4rem', justifyContent: 'center' }}>
                    <button
                      type="submit"
                      form={`edit-form-${food.id}`}
                      className="primary"
                      disabled={updateMutation.isPending}
                      style={{ fontSize: '0.8rem', padding: '0.3rem 0.6rem' }}
                    >
                      {t('foodDatabase.save')}
                    </button>
                    <button
                      type="button"
                      onClick={() => { setEditingId(null); setValidationError(null) }}
                      style={{ fontSize: '0.8rem', padding: '0.3rem 0.6rem' }}
                    >
                      {t('foodDatabase.cancel')}
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={food.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                  <td style={{ padding: '0.5rem' }}>{food.name}</td>
                  <td style={{ padding: '0.5rem', textAlign: 'right' }}>{food.portionGrams}g</td>
                  <td style={{ padding: '0.5rem', textAlign: 'right' }}>{food.carbsPer100g}g</td>
                  <td style={{ padding: '0.5rem', textAlign: 'right' }}>{food.carbsForPortion.toFixed(1)}g</td>
                  <td style={{ padding: '0.5rem', textAlign: 'center' }}>
                    <div style={{ display: 'flex', gap: '0.4rem', justifyContent: 'center' }}>
                      <button
                        onClick={() => startEdit(food)}
                        style={{ fontSize: '0.8rem', padding: '0.3rem 0.6rem' }}
                      >
                        {t('foodDatabase.edit')}
                      </button>
                      <button
                        onClick={() => handleDelete(food)}
                        style={{ fontSize: '0.8rem', padding: '0.3rem 0.6rem', color: 'var(--color-danger, red)' }}
                        disabled={deleteMutation.isPending}
                      >
                        {t('foodDatabase.delete')}
                      </button>
                    </div>
                  </td>
                </tr>
              )
            )}
          </tbody>
        </table>
      )}
      <ConfirmModal
        isOpen={deleteConfirmId !== null}
        title={t('confirm.archiveTitle')}
        message={t('foodDatabase.confirmDelete')}
        onConfirm={() => { if (deleteConfirmId) deleteMutation.mutate(deleteConfirmId); setDeleteConfirmId(null) }}
        onCancel={() => setDeleteConfirmId(null)}
      />
    </div>
  )
}
