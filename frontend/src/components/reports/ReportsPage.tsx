import { useState } from 'react'
import { Box, Stack, Tab, Tabs } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Bodega } from '../../generated/entidades'
import { PageHeader } from '../PageHeader'
import { MovementsReport } from './MovementsReport'
import { StockReport } from './StockReport'

export interface ReportReferences {
  bodegas: Bodega[]
  paises: ReferenceOption[]
  monedas: ReferenceOption[]
  productos: ReferenceOption[]
  usuarios: ReferenceOption[]
}

type ReportTab = 'stock' | 'movements'

export function ReportsPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<ReportTab>('stock')
  // Referencias para los filtros; si alguna no está permitida, su filtro se oculta.
  const bodegas = useApiQuery<PageResponse<Bodega>>('/api/v1/inventario/bodegas?size=100')
  const paises = useApiQuery<ReferenceOption[]>('/api/v1/referencias/pais')
  const monedas = useApiQuery<ReferenceOption[]>('/api/v1/referencias/moneda')
  const productos = useApiQuery<ReferenceOption[]>('/api/v1/referencias/producto')
  const usuarios = useApiQuery<ReferenceOption[]>('/api/v1/referencias/usuario')
  const references: ReportReferences = {
    bodegas: bodegas.data?.content ?? [],
    paises: paises.data ?? [],
    monedas: monedas.data ?? [],
    productos: productos.data ?? [],
    usuarios: usuarios.data ?? [],
  }

  return (
    <Stack spacing={3}>
      <PageHeader
        section={t('modules.reports.title')}
        title={t('reports.title')}
        subtitle={t('reports.subtitle')}
      />
      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} onChange={(_, value: ReportTab) => setTab(value)}>
          <Tab value="stock" label={t('reports.stockTab')} />
          <Tab value="movements" label={t('reports.movementsTab')} />
        </Tabs>
      </Box>
      {tab === 'stock' ? (
        <StockReport references={references} />
      ) : (
        <MovementsReport references={references} />
      )}
    </Stack>
  )
}
