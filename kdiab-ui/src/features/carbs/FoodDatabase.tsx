import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { carbsApi } from '../../api/carbsApi'
import type { FoodEntryResponse } from '../../api/carbsApi'
import { ConfirmModal } from '../../components/ConfirmModal'
import { AddFoodModal } from './AddFoodModal'

interface FoodDatabaseProps {
  userId: string
}

export const FoodDatabase: React.FC<FoodDatabaseProps> = ({ userId }) => {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [showModal, setShowModal] = useState(false)
  const [editingFood, setEditingFood] = useState<FoodEntryResponse | null>(null)
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
      setShowModal(false)
      setEditingFood(null)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ foodId, body }: { foodId: string; body: { name: string; portionGrams: number; carbsPer100g: number } }) =>
      carbsApi.updateFood(userId, foodId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['foods', userId] })
      setShowModal(false)
      setEditingFood(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (foodId: string) => carbsApi.deleteFood(userId, foodId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['foods', userId] })
    },
  })

  const handleAddClick = () => {
    setEditingFood(null)
    setShowModal(true)
  }

  const handleEditClick = (food: FoodEntryResponse) => {
    setEditingFood(food)
    setShowModal(true)
  }

  const handleModalSave = (data: { name: string; portionGrams: number; carbsPer100g: number }) => {
    if (editingFood) {
      updateMutation.mutate({ foodId: editingFood.id, body: data })
    } else {
      createMutation.mutate(data)
    }
  }

  const handleModalCancel = () => {
    setShowModal(false)
    setEditingFood(null)
  }

  const handleDelete = (food: FoodEntryResponse) => {
    setDeleteConfirmId(food.id)
  }

  if (isLoading) {
    return <p style={{ color: 'var(--text-secondary)' }}>{t('foodDatabase.loading')}</p>
  }

  if (isError) {
    return <p style={{ color: 'var(--color-danger, red)' }}>{t('foodDatabase.error')}</p>
  }

  const items = data?.items ?? []
  const isSaving = createMutation.isPending || updateMutation.isPending

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2 style={{ margin: 0 }}>{t('foodDatabase.title')}</h2>
        <button className="primary" onClick={handleAddClick}>
          {t('foodDatabase.addFood')}
        </button>
      </div>

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
            {items.map((food) => (
              <tr key={food.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                <td style={{ padding: '0.5rem' }}>{food.name}</td>
                <td style={{ padding: '0.5rem', textAlign: 'right' }}>{food.portionGrams}g</td>
                <td style={{ padding: '0.5rem', textAlign: 'right' }}>{food.carbsPer100g}g</td>
                <td style={{ padding: '0.5rem', textAlign: 'right' }}>{food.carbsForPortion.toFixed(1)}g</td>
                <td style={{ padding: '0.5rem', textAlign: 'center' }}>
                  <div style={{ display: 'flex', gap: '0.4rem', justifyContent: 'center' }}>
                    <button
                      onClick={() => handleEditClick(food)}
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
            ))}
          </tbody>
        </table>
      )}

      <AddFoodModal
        isOpen={showModal}
        initialFood={editingFood}
        onSave={handleModalSave}
        onCancel={handleModalCancel}
        isSaving={isSaving}
      />

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
