import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"

function App() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-neutral-50 dark:bg-neutral-900 p-4">
      <Card className="w-[380px] shadow-lg">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-xl font-bold">Chess Rivals Setup</CardTitle>
          <Badge variant="outline">v0.0.0</Badge>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            Client setup is complete. Tailwind CSS v4, shadcn/ui components, and structure are verified.
          </p>
          <div className="flex gap-2">
            <Button className="w-full">Get Started</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

export default App
