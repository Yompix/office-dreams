// Solana libraries for public/launch.html, bundled into public/launch-lib.js (see scripts/launch/README.md)
import { Buffer } from 'buffer';
import * as web3 from '@solana/web3.js';
import * as spl from '@solana/spl-token';
if (typeof window !== 'undefined' && !window.Buffer) window.Buffer = Buffer;
export { web3, spl, Buffer };
