import { prisma } from './db';
import bcrypt from 'bcryptjs';

async function seed() {
  const email = 'admin@nfcgate.app';
  const existing = await prisma.account.findUnique({ where: { email } });

  if (existing) {
    console.log('Admin account already exists.');
    return;
  }

  const hashedPassword = await bcrypt.hash('admin123', 12);

  await prisma.account.create({
    data: {
      email,
      password: hashedPassword,
      role: 'ADMIN',
    },
  });

  console.log('✅ Admin account created: admin@nfcgate.app / admin123');
}

seed()
  .catch(console.error)
  .finally(() => prisma.$disconnect());
