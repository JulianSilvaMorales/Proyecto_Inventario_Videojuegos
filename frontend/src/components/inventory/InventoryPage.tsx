import { useState } from 'react'
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Bodega, CrudEntityName, Proveedor } from '../../generated/entidades'
import { PageHeader } from '../PageHeader'
import { StockAdjustmentsTab } from './StockAdjustmentsTab'
import { StockBalancesTab } from './StockBalancesTab'
import { StockEntriesTab } from './StockEntriesTab'
import { StockExitsTab } from './StockExitsTab'
import { StockMovementsTab } from './StockMovementsTab'

interface InventoryPageProps {
  permissions: readonly string[]
  roles: readonly string[]
  canOpenWarehouses: boolean
  onOpenCrudEntity: (entityName: CrudEntityName) => void
}

type InventoryTab = 'balances' | 'entries' | 'exits' | 'adjustments' | 'movements' | 'warehouses'

export function InventoryPage({
  permissions,
  roles,
  canOpenWarehouses,
  onOpenCrudEntity,
}: InventoryPageProps) {
  const { t } = useTranslation()
  const canManagePurchases = permissions.includes('COMPRAS_GESTIONAR')
  const canManageExits = permissions.includes('SALIDAS_INVENTARIO_GESTIONAR')

  const tabs: InventoryTab[] = [
    'balances',
    ...(canManagePurchases ? (['entries'] as const) : []),
    ...(canManageExits ? (['exits'] as const) : []),
    'adjustments',
    'movements',
    ...(canOpenWarehouses ? (['warehouses'] as const) : []),
  ]
  const [selectedTab, setSelectedTab] = useState<InventoryTab>('balances')
  const activeTab = tabs.includes(selectedTab) ? selectedTab : 'balances'

  const bodegasQuery = useApiQuery<PageResponse<Bodega>>('/api/v1/inventario/bodegas?size=100')
  // Las referencias opcionales se omiten si el rol no tiene acceso a ellas.
  const paisesQuery = useApiQuery<ReferenceOption[]>('/api/v1/referencias/pais')
  const productosQuery = useApiQuery<ReferenceOption[]>('/api/v1/referencias/producto')
  const proveedoresQuery = useApiQuery<PageResponse<Proveedor>>(
    canManageExits ? '/api/v1/proveedores?size=100' : null,
  )

  const bodegas = (bodegasQuery.data?.content ?? []).filter((bodega) => bodega.activo)
  const paises = paisesQuery.data ?? []
  const productos = productosQuery.data ?? []
  const proveedores = (proveedoresQuery.data?.content ?? []).filter((proveedor) => proveedor.activo)

  return (
    <Stack spacing={3}>
      <PageHeader
        section={t('modules.inventory.title')}
        title={t('inventory.title')}
        subtitle={t('inventory.subtitle')}
      />

      {bodegasQuery.error && <Alert severity="error">{bodegasQuery.error}</Alert>}

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs
          value={activeTab}
          onChange={(_, value: InventoryTab) => setSelectedTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          {tabs.map((tab) => (
            <Tab key={tab} value={tab} label={t(`inventory.tabs.${tab}`)} />
          ))}
        </Tabs>
      </Box>

      {bodegasQuery.loading && !bodegasQuery.data ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress aria-label={t('common.loading')} />
        </Box>
      ) : (
        <>
          {activeTab === 'balances' && (
            <StockBalancesTab bodegas={bodegas} paises={paises} productos={productos} />
          )}
          {activeTab === 'entries' && <StockEntriesTab bodegas={bodegas} />}
          {activeTab === 'exits' && (
            <StockExitsTab bodegas={bodegas} proveedores={proveedores} productos={productos} />
          )}
          {activeTab === 'adjustments' && (
            <StockAdjustmentsTab
              bodegas={bodegas}
              productos={productos}
              permissions={permissions}
              roles={roles}
            />
          )}
          {activeTab === 'movements' && (
            <StockMovementsTab bodegas={bodegas} productos={productos} />
          )}
          {activeTab === 'warehouses' && (
            <Paper
              elevation={0}
              sx={{ p: 4, border: '1px solid', borderColor: 'divider', textAlign: 'center' }}
            >
              <Typography variant="h6">{t('inventory.tabs.warehouses')}</Typography>
              <Typography color="text.secondary" sx={{ mt: 1, mb: 3 }}>
                {t('inventory.warehousesDescription')}
              </Typography>
              <Button variant="contained" onClick={() => onOpenCrudEntity('bodega')}>
                {t('inventory.openWarehouses')}
              </Button>
            </Paper>
          )}
        </>
      )}
    </Stack>
  )
}
