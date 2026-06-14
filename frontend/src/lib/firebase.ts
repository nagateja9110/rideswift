import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';

// Firebase web config is public by design (it's embedded in the client). Phone
// auth is gated by Firebase's own reCAPTCHA + authorized-domains, not by secrecy.
// Override per-environment via VITE_FIREBASE_* if you ever need to.
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? 'AIzaSyAfYc4jMBM6mPHBL5RXyx8GHhMYs8__0OI',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? 'rideswift-2d539.firebaseapp.com',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? 'rideswift-2d539',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? 'rideswift-2d539.firebasestorage.app',
  messagingSenderId: import.meta.env.VITE_FIREBASE_SENDER_ID ?? '724708230924',
  appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '1:724708230924:web:831918a025a30bed6aeb29',
};

export const firebaseApp = initializeApp(firebaseConfig);
export const firebaseAuth = getAuth(firebaseApp);
