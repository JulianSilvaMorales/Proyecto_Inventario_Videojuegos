import { Alert, Box, Divider, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ReferenceOption } from '../../api/types'
import { formatDate } from '../../utils/formatters'
import { SaleLinesTable } from './SaleLinesTable'
import type { RespuestaVenta } from './salesTypes'

interface SaleReceiptProps {
  recibo: RespuestaVenta
  productos: ReferenceOption[]
  clienteNombre?: string
  decimales?: number
}

/** Recibo simulado (no es un documento fiscal); la clase `receipt-print` define lo que se imprime. */
export function SaleReceipt({ recibo, productos, clienteNombre, decimales }: SaleReceiptProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage ?? i18n.language
  const { venta } = recibo
  const confirmada = venta.confirmadaEnUtc ?? venta.creadaEnUtc

  return (
    <Box className="receipt-print">
      <Stack spacing={2}>
        <Box sx={{ textAlign: 'center' }}>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            {t('app.name')}
          </Typography>
          <Typography variant="subtitle1">{t('sales.receiptTitle')}</Typography>
        </Box>
        <Alert severity="info">{t('sales.simulatedReceipt')}</Alert>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' },
            gap: 1,
          }}
        >
          <Typography variant="body2">
            <strong>{t('sales.number')}:</strong> {venta.numero}
          </Typography>
          <Typography variant="body2">
            <strong>{t('sales.date')}:</strong> {formatDate(new Date(confirmada), language)}
          </Typography>
          <Typography variant="body2">
            <strong>{t('common.country')}:</strong> {venta.paisCodigo}
          </Typography>
          <Typography variant="body2">
            <strong>{t('sales.currency')}:</strong> {venta.monedaCodigo}
          </Typography>
          <Typography variant="body2">
            <strong>{t('sales.paymentMethod')}:</strong>{' '}
            {venta.medioPago
              ? t(`sales.paymentMethods.${venta.medioPago}`, { defaultValue: venta.medioPago })
              : '—'}
          </Typography>
          <Typography variant="body2">
            <strong>{t('sales.client')}:</strong> {clienteNombre ?? t('sales.noClient')}
          </Typography>
        </Box>
        <Divider />
        <SaleLinesTable detalle={recibo} productos={productos} decimales={decimales} />
      </Stack>
    </Box>
  )
}
