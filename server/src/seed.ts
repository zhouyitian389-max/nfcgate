import bcrypt from 'bcryptjs';
import prisma from './db.js';

async function seed() {
  const email = process.env.SEED_ADMIN_EMAIL || 'admin@nfcgate.local';
  const password = process.env.SEED_ADMIN_PASSWORD || 'admin12345';

  const exists = await prisma.user.findUnique({ where: { email } });
  if (exists) {
    console.log('Admin user already exists');
    return;
  }

  const hash = await bcrypt.hash(password, 12);
  await prisma.account.create({
    data: {
      name: 'Default Account',
      users: {
        create: {
          email,
          password: hash,
          role: 'ADMIN',
          name: 'Administrator'
        }
      }
    }
  });

  console.log(`Seed admin created: ${email}`);
}

seed()
  .catch((err) => {
    console.error(err);
    process.exitCode = 1;
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
