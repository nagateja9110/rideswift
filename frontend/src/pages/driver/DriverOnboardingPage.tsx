import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Car, Loader2 } from 'lucide-react';
import { driverApi } from '@/api/endpoints';
import { apiError } from '@/api/client';
import { Button } from '@/components/ui/button';
import { Input, Label, Select } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useToast } from '@/components/ui/toast';
import type { VehicleType } from '@/types';

export function DriverOnboardingPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [checking, setChecking] = useState(true);
  const [step, setStep] = useState<'license' | 'vehicle'>('license');
  const [driverId, setDriverId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const [license, setLicense] = useState('');
  const [make, setMake] = useState('');
  const [model, setModel] = useState('');
  const [year, setYear] = useState(2022);
  const [plate, setPlate] = useState('');
  const [vehicleType, setVehicleType] = useState<VehicleType>('ECONOMY');

  // If already onboarded, skip to the dashboard.
  useEffect(() => {
    driverApi
      .me()
      .then(() => navigate('/driver'))
      .catch(() => setChecking(false));
  }, [navigate]);

  const submitLicense = async () => {
    if (!license.trim()) return;
    setLoading(true);
    try {
      const d = await driverApi.register(license.trim());
      setDriverId(d.id);
      setStep('vehicle');
      toast('Driver profile created', 'success');
    } catch (e) {
      toast(apiError(e), 'error');
    } finally {
      setLoading(false);
    }
  };

  const submitVehicle = async () => {
    if (!driverId) return;
    setLoading(true);
    try {
      await driverApi.addVehicle(driverId, { make, model, year, licensePlate: plate, vehicleType });
      toast('Vehicle added — awaiting verification', 'success');
      navigate('/driver');
    } catch (e) {
      toast(apiError(e), 'error');
    } finally {
      setLoading(false);
    }
  };

  if (checking) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader2 className="h-7 w-7 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-6">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 flex h-11 w-11 items-center justify-center rounded-lg bg-primary/15 text-primary">
            <Car className="h-6 w-6" />
          </div>
          <CardTitle>Become a driver</CardTitle>
          <CardDescription>
            {step === 'license' ? 'Step 1 of 2 — Your license' : 'Step 2 of 2 — Your vehicle'}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {step === 'license' ? (
            <>
              <div className="space-y-1.5">
                <Label>Driver license number</Label>
                <Input value={license} onChange={(e) => setLicense(e.target.value)} placeholder="DL-12345678" />
              </div>
              <Button className="w-full" loading={loading} onClick={submitLicense}>
                Continue
              </Button>
            </>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label>Make</Label>
                  <Input value={make} onChange={(e) => setMake(e.target.value)} placeholder="Toyota" />
                </div>
                <div className="space-y-1.5">
                  <Label>Model</Label>
                  <Input value={model} onChange={(e) => setModel(e.target.value)} placeholder="Camry" />
                </div>
                <div className="space-y-1.5">
                  <Label>Year</Label>
                  <Input type="number" value={year} onChange={(e) => setYear(Number(e.target.value))} />
                </div>
                <div className="space-y-1.5">
                  <Label>Plate</Label>
                  <Input value={plate} onChange={(e) => setPlate(e.target.value)} placeholder="ABC-1234" />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>Vehicle class</Label>
                <Select value={vehicleType} onChange={(e) => setVehicleType(e.target.value as VehicleType)}>
                  <option value="ECONOMY">Economy</option>
                  <option value="PREMIUM">Premium</option>
                  <option value="XL">XL</option>
                </Select>
              </div>
              <Button className="w-full" loading={loading} onClick={submitVehicle}>
                Finish & submit for verification
              </Button>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
