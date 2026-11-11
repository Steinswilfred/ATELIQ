import { Button } from '../components/ui/Button'
import { useAuth } from '../store/authStore'
import { useNavigate } from 'react-router-dom'

export default function Account() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <div>
      <h1 className="text-3xl font-bold">Hello, {user?.fullName}</h1>
      <p className="mt-2 text-sm">{user?.email}</p>
      <p className="mt-6 text-sm">Orders and order tracking arrive in a later phase. Your saved designs and measurements are available now.</p>
      <Button className="mt-6" variant="ghost" onClick={async () => { await logout(); navigate('/') }}>Sign out</Button>
    </div>
  )
}
