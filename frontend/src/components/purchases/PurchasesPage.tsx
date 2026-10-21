import { useState } from 'react'
import { Alert, Box, Stack, Tab, Tabs } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { PageResponse, ReferenceOption } from '../../api/types'
import { useApiQuery } from '../../api/useApiQuery'
import type { Proveedor } from '../../generated/entidades'
import { PageHeader } from '../PageHeader'
import { PurchaseOrdersTab } from './PurchaseOrdersTab'
import { SupplierProductsTab } from './SupplierProductsTab'
import type { ContextoCompras } from './types'

interface PurchasesPageProps {
  permissions: readonly string[]
  onOpenInventory?: () => void
}

type PurchasesTab = 'orders' | 'supplierProducts'

export function PurchasesPage({ permissions, onOpenInventory }: PurchasesPageProps) {
  const { t } = useTranslation()
  const canManageSuppliers = permissions.includes('PROVEEDORES_GESTIONAR')
  const canReadSuppliers =
    canManageSuppliers || permissions.includes('PROVEEDORES_CONSULTAR')
  const [activeTab, setActiveTab] = useState<PurchasesTab>('orders')

  const proveedoresQuery = useApiQuery<PageResponse<Proveedor>>(
    canReadSuppliers ? '/api/v1/proveedores?size=100' : null,
  )
  const monedasQuery = useApiQuery<ReferenceOption[]>('/api/v1/referencias/moneda')
  const contextoQuery = useApiQuery<ContextoCompras>('/api/v1/compras/contexto')

  const proveedores = proveedoresQuery.data?.content ?? []
  const monedas = monedasQuery.data ?? []
  const error = proveedoresQuery.error || monedasQuery.error || contextoQuery.error

  return (
    <Stack spacing={3}>
      <PageHeader
        section={t('modules.purchases.title')}
        title={t('purchases.title')}
        subtitle={t('purchases.subtitle')}
      />

      {error && <Alert severity="error">{error}</Alert>}
      {!canReadSuppliers && <Alert severity="info">{t('purchases.noSupplierAccess')}</Alert>}

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs
          value={activeTab}
          onChange={(_, value: PurchasesTab) => setActiveTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab value="orders" label={t('purchases.tabs.orders')} />
          <Tab value="supplierProducts" label={t('purchases.tabs.supplierProducts')} />
        </Tabs>
      </Box>

      {activeTab === 'orders' ? (
        <PurchaseOrdersTab
          proveedores={proveedores}
          monedas={monedas}
          monedaBaseCodigo={contextoQuery.data?.monedaBaseCodigo ?? null}
          onOpenInventory={onOpenInventory}
        />
      ) : (
        <SupplierProductsTab proveedores={proveedores} canManage={canManageSuppliers} />
      )}
    </Stack>
  )
}
